package simulator

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// Golden vectors copied from cards/vendor-cli/muuncardv3/cert_test.go, the
// fixtures the reference implementation pins its own wire format against. The
// card stores the blob verbatim and verifiers bind SHA-256 of these exact
// bytes, so an encoding change there is a compatibility break even when the
// output is still valid CBOR.
const (
	goldenProtectedHeaderHex = "a10126"
	goldenBlobPrefixHex      = "8443a10126a0"
	goldenPayloadHex         = "a5" + // the CWT: a map of five claims
		"0150a0a1a2a3a4a5a6a7a8a9aaabacadaeaf" + // iss: device_model_uuid
		"02500102030405060708090a0b0c0d0e0f10" + // sub: serial_number
		"061a6553f100" + // iat
		"08a40102200121" + // cnf: an EC2 COSE_Key on P-256,
		"58206b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" + // its x,
		"22f5" + // and an odd y
		"20a50102048102200121" + // commitment: an EC2 COSE_Key with key_ops [verify],
		"58207cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978" + // its x,
		"22f5" // and an odd y

	// The cnf key's x is P-256's generator, and the certificate declares an
	// odd y, so decompression has to land on exactly 0x04 || Gx || Gy. That
	// makes the vector check the decompression and not just the parse.
	p256GeneratorX = "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
	p256GeneratorY = "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
)

func TestParseVendorCertificateV3_0_GoldenVector(t *testing.T) {
	certificate := goldenCertificateV3_0(t, decodeHex(t, goldenPayloadHex))

	parsed, err := parseVendorCertificateV3_0(certificate)
	require.NoError(t, err)

	require.Equal(t, decodeHex(t, "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"), parsed.DeviceModelUUID)
	require.Equal(t, decodeHex(t, "0102030405060708090a0b0c0d0e0f10"), parsed.SerialNumber)
	require.Equal(t, uint32(0x6553F100), parsed.IssuedAt)

	// cnf carries the generator with an odd y.
	require.Equal(
		t,
		decodeHex(t, "04"+p256GeneratorX+p256GeneratorY),
		parsed.KeyAgreementPub,
	)

	// The commitment key is a different point, and it comes back uncompressed
	// and on the curve.
	require.Len(t, parsed.CommitmentPub, secp256r1PointSize)
	require.Equal(t, byte(0x04), parsed.CommitmentPub[0])
	require.Equal(
		t,
		decodeHex(t, "7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978"),
		parsed.CommitmentPub[1:33],
	)
	require.NotEqual(t, parsed.KeyAgreementPub, parsed.CommitmentPub)
}

func TestParseVendorCertificateV3_0_ErrorScenarios(t *testing.T) {
	validPayload := decodeHex(t, goldenPayloadHex)

	tests := []struct {
		name        string
		certificate []byte
		expectError string
	}{
		{
			name:        "empty blob",
			certificate: []byte{},
			expectError: "vendor certificate is empty",
		},
		{
			name:        "blob longer than a card can store",
			certificate: make([]byte, vendorCertificateMaxSizeV3_0+1),
			expectError: "over the 243 a card can store",
		},
		{
			name:        "not CBOR at all",
			certificate: []byte{0xFF, 0xFF, 0xFF},
			expectError: "decode COSE_Sign1",
		},
		{
			name: "signature is not ES256-sized",
			certificate: goldenCertificateWithSignatureV3_0(
				t, validPayload, make([]byte, coseES256SignatureLen-1),
			),
			expectError: "ES256 signature must be 64 bytes, got 63",
		},
		{
			name: "unprotected headers are not empty",
			certificate: func() []byte {
				// Swap the 0xA0 empty map for {1: 1}.
				blob := goldenCertificateV3_0(t, validPayload)
				withHeader := append([]byte{}, blob[:5]...)
				withHeader = append(withHeader, 0xA1, 0x01, 0x01)
				return append(withHeader, blob[6:]...)
			}(),
			expectError: "unprotected headers must be empty",
		},
		{
			name: "cnf key is on a curve we do not speak",
			certificate: func() []byte {
				// crv P-256(1) -> P-384(2), leaving everything else alone.
				payload := append([]byte{}, validPayload...)
				// In a4 01 02 20 01 21, the 0x20 is key -1 and the
				// 0x01 after it is its value.
				at := indexOfSubslice(t, payload, decodeHex(t, "a40102200121"))
				payload[at+4] = 0x02 // P-384
				return goldenCertificateV3_0(t, payload)
			}(),
			expectError: "crv must be P-256(1), got 2",
		},
		{
			name: "cnf key has no y parity",
			certificate: func() []byte {
				// Drop key -3 and its bool from the cnf COSE_Key, and shrink
				// that map from 4 entries to 3. Without the parity the key
				// cannot be decompressed.
				payload := append([]byte{}, validPayload...)
				at := indexOfSubslice(t, payload, decodeHex(t, "a40102200121"))
				payload[at] = 0xA3
				parityAt := at + 6 + 2 + 32 // past the map head, the x head and x itself
				require.Equal(t, []byte{0x22, 0xF5}, payload[parityAt:parityAt+2])
				return goldenCertificateV3_0(t, append(payload[:parityAt], payload[parityAt+2:]...))
			}(),
			expectError: "y parity is missing",
		},
		{
			name: "iat claim is missing",
			certificate: func() []byte {
				// Drop key 6 and its uint32, and shrink the map to 4 entries.
				payload := append([]byte{}, validPayload...)
				at := indexOfSubslice(t, payload, decodeHex(t, "061a6553f100"))
				trimmed := append([]byte{0xA4}, payload[1:at]...)
				return goldenCertificateV3_0(t, append(trimmed, payload[at+6:]...))
			}(),
			expectError: "missing the iat claim",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			parsed, err := parseVendorCertificateV3_0(tt.certificate)
			require.Error(t, err)
			require.Nil(t, parsed)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestNewVendorCertificateV3_0_ErrorScenarios(t *testing.T) {
	generator := decodeHex(t, "04"+p256GeneratorX+p256GeneratorY)
	offCurve := make([]byte, secp256r1PointSize)
	offCurve[0] = 0x04

	tests := []struct {
		name            string
		deviceModelUUID []byte
		serialNumber    []byte
		keyAgreementPub []byte
		commitmentPub   []byte
		expectError     string
	}{
		{
			name:            "device model UUID is not 16 bytes",
			deviceModelUUID: make([]byte, deviceModelUUIDSizeV3_0-1),
			serialNumber:    make([]byte, serialNumberSizeV3_0),
			keyAgreementPub: generator,
			commitmentPub:   generator,
			expectError:     "device model UUID must be 16 bytes, got 15",
		},
		{
			name:            "serial number is not 16 bytes",
			deviceModelUUID: make([]byte, deviceModelUUIDSizeV3_0),
			serialNumber:    make([]byte, serialNumberSizeV3_0+1),
			keyAgreementPub: generator,
			commitmentPub:   generator,
			expectError:     "serial number must be 16 bytes, got 17",
		},
		{
			name:            "key agreement key is off the curve",
			deviceModelUUID: make([]byte, deviceModelUUIDSizeV3_0),
			serialNumber:    make([]byte, serialNumberSizeV3_0),
			keyAgreementPub: offCurve,
			commitmentPub:   generator,
			expectError:     "key agreement key:",
		},
		{
			name:            "commitment key is off the curve",
			deviceModelUUID: make([]byte, deviceModelUUIDSizeV3_0),
			serialNumber:    make([]byte, serialNumberSizeV3_0),
			keyAgreementPub: generator,
			commitmentPub:   offCurve,
			expectError:     "commitment key:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			certificate, err := newVendorCertificateV3_0(
				tt.deviceModelUUID, tt.serialNumber, 1, tt.keyAgreementPub, tt.commitmentPub,
			)
			require.Error(t, err)
			require.Nil(t, certificate)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

//==========================
// Private, helper functions
//==========================

func decodeHex(t *testing.T, s string) []byte {
	t.Helper()
	decoded, err := hex.DecodeString(s)
	require.NoError(t, err)
	return decoded
}

// goldenCertificateV3_0 wraps a CWT payload into a COSE_Sign1 blob with a
// placeholder signature. Nothing verifies the signature, so its bytes only
// have to be the right length.
func goldenCertificateV3_0(t *testing.T, payload []byte) []byte {
	t.Helper()
	return goldenCertificateWithSignatureV3_0(t, payload, make([]byte, coseES256SignatureLen))
}

func goldenCertificateWithSignatureV3_0(t *testing.T, payload, signature []byte) []byte {
	t.Helper()

	blob := decodeHex(t, goldenBlobPrefixHex)
	blob = append(blob, cborByteStringHeader(t, len(payload))...)
	blob = append(blob, payload...)
	blob = append(blob, cborByteStringHeader(t, len(signature))...)
	return append(blob, signature...)
}

// cborByteStringHeader encodes a definite-length byte string header for the
// lengths these fixtures use: one-byte for under 24, then the 8- and 16-bit
// forms.
func cborByteStringHeader(t *testing.T, length int) []byte {
	t.Helper()

	const majorTypeByteString = 0x40
	switch {
	case length < 24:
		return []byte{majorTypeByteString | byte(length)}
	case length < 256:
		return []byte{majorTypeByteString | 24, byte(length)}
	default:
		return binary.BigEndian.AppendUint16([]byte{majorTypeByteString | 25}, uint16(length))
	}
}

func indexOfSubslice(t *testing.T, haystack, needle []byte) int {
	t.Helper()
	at := bytes.Index(haystack, needle)
	require.NotEqual(t, -1, at, "subslice not found in fixture")
	return at
}
