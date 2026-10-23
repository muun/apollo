package tlsdistrust

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/pem"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestTransportRefusesChainThroughDistrustedIntermediate(t *testing.T) {
	pki := newTestPKI(t)
	server := pki.startServer(t)

	blocklist := spkiBlocklist{spkiHashHex(pki.intermediate): {}}
	client := &http.Client{Transport: blocklist.newTransport(pki.roots())}

	err := get(t, client, server.URL)

	require.ErrorIs(t, err, ErrDistrustedCertificate)
}

func TestTransportAcceptsChainWithoutDistrustedCertificates(t *testing.T) {
	pki := newTestPKI(t)
	server := pki.startServer(t)

	client := &http.Client{Transport: distrusted.newTransport(pki.roots())}

	err := get(t, client, server.URL)

	require.NoError(t, err)
}

func TestVerifyConnectionAcceptsWhenOneChainAvoidsTheBlocklist(t *testing.T) {
	pki := newTestPKI(t)
	blocklist := spkiBlocklist{spkiHashHex(pki.intermediate): {}}

	err := blocklist.verifyConnection(tls.ConnectionState{
		VerifiedChains: [][]*x509.Certificate{
			{pki.leaf, pki.intermediate, pki.root},
			{pki.leaf, pki.root},
		},
	})

	require.NoError(t, err)
}

func TestVerifyConnectionRefusesWithoutVerifiedChains(t *testing.T) {
	err := distrusted.verifyConnection(tls.ConnectionState{})

	require.ErrorIs(t, err, ErrDistrustedCertificate)
}

func TestDistrustedMatchesGoBlocklist(t *testing.T) {
	// The eight SPKI hashes of https://go.dev/cl/851047, which a typo here would silently drop.
	goBlocklist := []string{
		"ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
		"49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
		"e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
		"5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
		"43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
		"63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
		"ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6",
		"92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
	}

	require.Len(t, distrusted, len(goBlocklist))
	for _, hash := range goBlocklist {
		require.Contains(t, distrusted, hash)
	}
}

func TestDistrustedContainsRealEMudhraIntermediate(t *testing.T) {
	// The public EM DV TLS CA - G3B certificate, SHA-256 fingerprint
	// 42c4accd12c4f59597925e1a08a7a90102608543809308d50ec427e118ba1493.
	block, _ := pem.Decode([]byte(emDVTLSCAG3BPEM))
	require.NotNil(t, block)
	certificate, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)

	require.Equal(t, "EM DV TLS CA - G3B", certificate.Subject.CommonName)
	require.True(t, distrusted.contains(certificate))
}

// testPKI is a root -> intermediate -> leaf chain for 127.0.0.1.
type testPKI struct {
	root         *x509.Certificate
	intermediate *x509.Certificate
	leaf         *x509.Certificate
	leafKey      *ecdsa.PrivateKey
}

func newTestPKI(t *testing.T) *testPKI {
	t.Helper()

	rootKey := newTestKey(t)
	root := newTestCertificate(t, &x509.Certificate{
		Subject:               pkix.Name{CommonName: "test root"},
		IsCA:                  true,
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageCertSign,
	}, nil, rootKey, rootKey)

	intermediateKey := newTestKey(t)
	intermediate := newTestCertificate(t, &x509.Certificate{
		Subject:               pkix.Name{CommonName: "test intermediate"},
		IsCA:                  true,
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageCertSign,
	}, root, intermediateKey, rootKey)

	leafKey := newTestKey(t)
	leaf := newTestCertificate(t, &x509.Certificate{
		Subject:     pkix.Name{CommonName: "test leaf"},
		IPAddresses: []net.IP{net.IPv4(127, 0, 0, 1)},
		KeyUsage:    x509.KeyUsageDigitalSignature,
		ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}, intermediate, leafKey, intermediateKey)

	return &testPKI{root: root, intermediate: intermediate, leaf: leaf, leafKey: leafKey}
}

func (p *testPKI) roots() *x509.CertPool {
	pool := x509.NewCertPool()
	pool.AddCert(p.root)
	return pool
}

func (p *testPKI) startServer(t *testing.T) *httptest.Server {
	t.Helper()

	handler := func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}
	server := httptest.NewUnstartedServer(http.HandlerFunc(handler))
	server.TLS = &tls.Config{
		Certificates: []tls.Certificate{{
			Certificate: [][]byte{p.leaf.Raw, p.intermediate.Raw},
			PrivateKey:  p.leafKey,
		}},
	}
	server.StartTLS()
	t.Cleanup(server.Close)
	return server
}

func newTestKey(t *testing.T) *ecdsa.PrivateKey {
	t.Helper()

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	return key
}

// newTestCertificate signs template with signerKey; a nil parent makes it self-signed.
func newTestCertificate(
	t *testing.T,
	template *x509.Certificate,
	parent *x509.Certificate,
	key *ecdsa.PrivateKey,
	signerKey *ecdsa.PrivateKey,
) *x509.Certificate {
	t.Helper()

	serial, err := rand.Int(rand.Reader, big.NewInt(1<<62))
	require.NoError(t, err)
	template.SerialNumber = serial
	template.NotBefore = time.Now().Add(-time.Hour)
	template.NotAfter = time.Now().Add(time.Hour)
	if parent == nil {
		parent = template
	}

	der, err := x509.CreateCertificate(rand.Reader, template, parent, &key.PublicKey, signerKey)
	require.NoError(t, err)
	certificate, err := x509.ParseCertificate(der)
	require.NoError(t, err)
	return certificate
}

// get fails unless the request completes with 200 OK.
func get(t *testing.T, client *http.Client, url string) error {
	t.Helper()

	request, err := http.NewRequestWithContext(t.Context(), http.MethodGet, url, nil)
	require.NoError(t, err)
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	require.NoError(t, response.Body.Close())
	require.Equal(t, http.StatusOK, response.StatusCode)
	return nil
}

func spkiHashHex(certificate *x509.Certificate) string {
	hash := sha256.Sum256(certificate.RawSubjectPublicKeyInfo)
	return hex.EncodeToString(hash[:])
}

// emDVTLSCAG3BPEM is the public EM DV TLS CA - G3B certificate, revoked for key compromise.
const emDVTLSCAG3BPEM = `-----BEGIN CERTIFICATE-----
MIIDaDCCAu6gAwIBAgIQAKSu5njyM2K0wORgt3LyFDAKBggqhkjOPQQDAzBWMQsw
CQYDVQQGEwJJTjElMCMGA1UECgwcZU11ZGhyYSBUZWNobm9sb2dpZXMgTGltaXRl
ZDEgMB4GA1UEAwwXZW1TaWduIFJvb3QgVExTIENBIC0gRzMwHhcNMjUwNDMwMTU1
NDA4WhcNNDAwNDI4MTgzMDAwWjA/MQswCQYDVQQGEwJOTDETMBEGA1UECgwKZU11
ZGhyYSBCVjEbMBkGA1UEAwwSRU0gRFYgVExTIENBIC0gRzNCMHYwEAYHKoZIzj0C
AQYFK4EEACIDYgAE557ZwEqXc8RzXlERHahawr1eZKs3S4DhLLwgOOweTw47gS/J
hZ4eZ6+wTi+LyX90hbplqpEe6KTkW2LVzAPVa+Z4s8OMtNyTfKJGDiOEU8iSk10p
1E+R4VYGtjzO2rRoo4IBljCCAZIwHwYDVR0jBBgwFoAU4TqrIXzx401UrtRyt/2L
WUIgkFQwHQYDVR0OBBYEFOIeL+ccGTkOVUtiwSAkVzUkiiUUMB0GA1UdJQQWMBQG
CCsGAQUFBwMBBggrBgEFBQcDAjBdBgNVHSAEVjBUMDkGCysGAQQBg44hAQABMCow
KAYIKwYBBQUHAgEWHGh0dHA6Ly9yZXBvc2l0b3J5LmVtc2lnbi5jb20wDQYLKwYB
BAGDjiEBAmQwCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAkBggrBgEFBQcwAYYY
aHR0cDovL29jc3AtYi5lbXNpZ24uY29tMD8GCCsGAQUFBzAChjNodHRwOi8vcmVw
b3NpdG9yeS5lbXNpZ24uY29tL2NlcnRzL0VNRFZUTFNDQUczQi5wN2MwNwYDVR0f
BDAwLjAsoCqgKIYmaHR0cDovL2NybC5lbXNpZ24uY29tLz9yb290dGxzY2FnMy5j
cmwwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQAwCgYIKoZIzj0E
AwMDaAAwZQIwLmsGXi6oFMJ2klkcEMXMug6gvxQWZWkewiDWVzQyl2L6z6DuQfPO
Ibh1grvdULDCAjEAxZvLlxUavG69J6wnSjC8XBbqNIQsJ6CE2QdNOe84Ptxb6VG8
gt2TlfJbHii+KNvK
-----END CERTIFICATE-----`
