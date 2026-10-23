// Package tlsdistrust provides the HTTP transport libwallet uses for its HTTPS requests. The
// transport refuses certificate chains that go through intermediates we distrust, even when the
// platform's root store still trusts their issuers.
//
// Go filters these intermediates itself since Go 1.26.10 and 1.27.3 (https://go.dev/issue/82345).
// This package keeps libwallet safe on older toolchains, and on Android, where Go verifies with
// the copy of roots in /system and never sees the blocklist Google ships through Play Services.
package tlsdistrust

import (
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"net/http"
	"slices"
	"sync"
)

// spkiBlocklist holds the hex SHA-256 of the SubjectPublicKeyInfo of each distrusted
// certificate. Hashing the key instead of the certificate also catches cross-signed copies.
type spkiBlocklist map[string]struct{}

// distrusted holds the eMudhra intermediates revoked for key compromise on 2026-10-18/19. The
// list is the one Go ships in crypto/x509 (https://go.dev/cl/851047).
var distrusted = spkiBlocklist{
	// C=NL, O=eMudhra BV, CN=EM DV TLS CA - G3B (issuer: emSign Root TLS CA - G3)
	"ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105": {},
	// C=US, O=eMudhra Inc, CN=EM DV TLS CA - G3C (issuer: emSign Root TLS CA - G3)
	"49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f": {},
	// C=NL, O=eMudhra BV, CN=EM EV TLS CA - G3B (issuer: emSign Root TLS CA - G3)
	"e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41": {},
	// C=US, O=eMudhra Inc, CN=EM EV TLS CA - G3C (issuer: emSign Root TLS CA - G3)
	"5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0": {},
	// C=NL, O=eMudhra BV, CN=EM OV TLS CA - G3B (issuer: emSign Root TLS CA - G3)
	"43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b": {},
	// C=US, O=eMudhra Inc, CN=EM OV TLS CA - G3C (issuer: emSign Root TLS CA - G3)
	"63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8": {},
	// C=NL, O=eMudhra BV, CN=EM OV CS CA - G3B (issuer: emSign Root CS CA - G3)
	"ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6": {},
	// C=US, O=eMudhra Inc, CN=EM OV CS CA - G3C (issuer: emSign Root CS CA - G3)
	"92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30": {},
}

var transport = sync.OnceValue(func() *http.Transport {
	return distrusted.newTransport(nil)
})

// Transport returns the shared transport for libwallet's HTTPS requests. It verifies servers
// against the platform's roots, like http.DefaultTransport, and then drops the chains that go
// through a distrusted certificate.
func Transport() *http.Transport {
	return transport()
}

// newTransport clones http.DefaultTransport to keep its proxy, HTTP/2 and timeout settings.
// A nil roots means the platform's roots.
func (b spkiBlocklist) newTransport(roots *x509.CertPool) *http.Transport {
	httpTransport := http.DefaultTransport.(*http.Transport).Clone()
	httpTransport.TLSClientConfig = &tls.Config{
		RootCAs:          roots,
		VerifyConnection: b.verifyConnection,
	}
	return httpTransport
}

// verifyConnection accepts the connection if at least one verified chain avoids every distrusted
// certificate. It also refuses connections without verified chains, which only happen when
// certificate verification is turned off.
func (b spkiBlocklist) verifyConnection(connectionState tls.ConnectionState) error {
	if !slices.ContainsFunc(connectionState.VerifiedChains, b.allows) {
		return ErrDistrustedCertificate
	}
	return nil
}

func (b spkiBlocklist) allows(chain []*x509.Certificate) bool {
	return !slices.ContainsFunc(chain, b.contains)
}

func (b spkiBlocklist) contains(certificate *x509.Certificate) bool {
	hash := sha256.Sum256(certificate.RawSubjectPublicKeyInfo)
	_, found := b[hex.EncodeToString(hash[:])]
	return found
}
