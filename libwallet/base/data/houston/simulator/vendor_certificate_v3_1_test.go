package simulator

import (
	"bytes"
	"testing"

	"github.com/fxamacker/cbor/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// The 3.1 certificate is the protocol spec's. Its reference vector is the one
// the vendor CLI pins, goldenCertificateV3_1Hex below. The fixtures are built
// here, with the deterministic encoding the spec requires and the vendor CLI
// uses, from the claims the spec lists, so that each test can change one claim
// at a time.

// specCertificateClaimsV3_1 are the claims a fixture certificate carries,
// with what the parser is expected to read out of them.
var (
	testDeviceModelIDV3_1    = []byte{0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7}
	testKeyAgreementRootV3_1 = bytes.Repeat([]byte{0xAB}, 32)
	testInclusionProofV3_1   = []byte{0x83, 0x18, 0x20, 0x00, 0x80} // [32, 0, []]
)

const testIssuedAtV3_1 = uint32(0x6553F100)

// goldenCertificateV3_1Hex is goldenCertHex from
// cards/vendor-cli/muuncardv3/cert_test.go, generated there by an independent
// CBOR encoder from fixed inputs: device_model_id 01..08, iat 1700000000,
// key_agreement_root 32 x 0xAA, commitment_pub G, oemid 67132, a signature of
// 64 x 0x11, and the inclusion proof of leaf 5.
const goldenCertificateV3_1Hex = "" +
	"8447a2012619018b01a119018ca1208158af83182005855820202020202020202020202020202020" +
	"20202020202020202020202020202020205820212121212121212121212121212121212121212121" +
	"21212121212121212121215820222222222222222222222222222222222222222222222222222222" +
	"22222222225820232323232323232323232323232323232323232323232323232323232323232358" +
	"202424242424242424242424242424242424242424242424242424242424242424586ea5061a6553" +
	"f10008a101a4010220012158206b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a139" +
	"45d898c29622f5190100582101aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
	"aaaaaaaaaa1901021a0001063c190103480102030405060708584011111111111111111111111111" +
	"11111111111111111111111111111111111111111111111111111111111111111111111111111111" +
	"1111111111111111111111"

func TestParseVendorCertificateV3_1_GoldenVector(t *testing.T) {
	// The vendor issues these bytes, so reading them is what pins this reader
	// to the vendor's encoder rather than to libwallet's own.
	parsed, err := parseVendorCertificateV3_1(decodeHex(t, goldenCertificateV3_1Hex))
	require.NoError(t, err)

	require.Equal(t, []byte{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, parsed.DeviceModelID)
	require.Equal(t, uint32(1_700_000_000), parsed.IssuedAt)
	require.Equal(t, bytes.Repeat([]byte{0xAA}, 32), parsed.KeyAgreementRoot)
	require.Equal(t, decodeHex(t, "04"+p256GeneratorX+p256GeneratorY), parsed.CommitmentPub)
}

func TestParseVendorCertificateV3_1_SpecFixture(t *testing.T) {
	certificate := specCertificateV3_1(t, specCertificatePayloadV3_1(t))

	parsed, err := parseVendorCertificateV3_1(certificate)
	require.NoError(t, err)

	require.Equal(t, testDeviceModelIDV3_1, parsed.DeviceModelID)
	require.Equal(t, testIssuedAtV3_1, parsed.IssuedAt)
	require.Equal(t, testKeyAgreementRootV3_1, parsed.KeyAgreementRoot)

	// cnf carries the generator with an odd y, so decompression has to land
	// on exactly 0x04 || Gx || Gy.
	require.Equal(t, decodeHex(t, "04"+p256GeneratorX+p256GeneratorY), parsed.CommitmentPub)
}

func TestParseVendorCertificateV3_1_ErrorScenarios(t *testing.T) {
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
			certificate: make([]byte, vendorCertificateMaxSizeV3_1+1),
			expectError: "over the 418 a card can store",
		},
		{
			name:        "not CBOR at all",
			certificate: []byte{0xFF, 0xFF, 0xFF},
			expectError: "decode COSE_Sign1",
		},
		{
			name: "signature is not ES256-sized",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				signature: make([]byte, coseES256SignatureLen-1),
			}),
			expectError: "ES256 signature must be 64 bytes, got 63",
		},
		{
			name: "data structure is not RFC 9162",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				protectedHeader: map[int]int{1: coseAlgorithmES256, coseHeaderVDS: 2},
			}),
			expectError: "expected data structure RFC9162_SHA256(1), got 2",
		},
		{
			name: "unprotected header carries no inclusion proof",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				unprotectedHeader: map[int]any{},
			}),
			expectError: "carries no inclusion proof",
		},
		{
			name: "unprotected header carries two inclusion proofs",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				unprotectedHeader: map[int]any{
					coseHeaderVDP: map[int]any{
						coseVDPInclusionProofs: [][]byte{
							testInclusionProofV3_1, testInclusionProofV3_1,
						},
					},
				},
			}),
			expectError: "expected one inclusion proof, got 2",
		},
		{
			name: "ueid is not a type byte and a root",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				ueid: append([]byte{ueidTypeV3_1}, make([]byte, 31)...),
			}),
			expectError: "ueid must be 33 bytes, got 32",
		},
		{
			name: "ueid is of another type",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				ueid: append([]byte{0x02}, testKeyAgreementRootV3_1...),
			}),
			expectError: "ueid type must be 0x01, got 0x02",
		},
		{
			name: "hwmodel is not a device model id",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				hardwareModel: make([]byte, deviceModelIDSizeV3_1-1),
			}),
			expectError: "device model id must be 8 bytes, got 7",
		},
		{
			name: "oemid claim is missing",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				withoutOEMID: true,
			}),
			expectError: "missing the oemid claim",
		},
		{
			name: "oemid is another vendor's",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				oemID: 12_345,
			}),
			expectError: "oemid must be Muun's 67132, got 12345",
		},
		{
			name: "cnf key has no y parity",
			certificate: specCertificateWithV3_1(t, specCertificateOptionsV3_1{
				commitmentKey: map[int]any{
					1:  coseKeyTypeEC2,
					-1: coseCurveP256,
					-2: decodeHex(t, p256GeneratorX),
				},
			}),
			expectError: "y parity is missing",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			parsed, err := parseVendorCertificateV3_1(tt.certificate)
			require.Error(t, err)
			require.Nil(t, parsed)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

//==========================
// Private, helper functions
//==========================

// specCertificateOptionsV3_1 is what a fixture can deviate from the spec
// in. A zero field keeps the spec's value.
type specCertificateOptionsV3_1 struct {
	protectedHeader   map[int]int
	unprotectedHeader map[int]any
	signature         []byte
	ueid              []byte
	hardwareModel     []byte
	withoutOEMID      bool
	oemID             uint64
	commitmentKey     map[int]any
}

// specCertificatePayloadV3_1 encodes the CWT the spec lists: iat, cnf with
// the commitment key, ueid over the key agreement root, oemid and hwmodel.
func specCertificatePayloadV3_1(t *testing.T) []byte {
	t.Helper()
	return specCertificatePayloadWithV3_1(t, specCertificateOptionsV3_1{})
}

func specCertificatePayloadWithV3_1(t *testing.T, options specCertificateOptionsV3_1) []byte {
	t.Helper()

	commitmentKey := options.commitmentKey
	if commitmentKey == nil {
		commitmentKey = map[int]any{
			1:  coseKeyTypeEC2,
			-1: coseCurveP256,
			-2: decodeHex(t, p256GeneratorX),
			-3: true, // the generator's y is odd
		}
	}
	ueid := options.ueid
	if ueid == nil {
		ueid = append([]byte{ueidTypeV3_1}, testKeyAgreementRootV3_1...)
	}
	hardwareModel := options.hardwareModel
	if hardwareModel == nil {
		hardwareModel = testDeviceModelIDV3_1
	}

	claims := map[int]any{
		6:   testIssuedAtV3_1,
		8:   map[int]any{cnfCOSEKey: commitmentKey},
		256: ueid,
		259: hardwareModel,
	}
	if !options.withoutOEMID {
		oemID := options.oemID
		if oemID == 0 {
			oemID = muunOEMID
		}
		claims[258] = oemID
	}

	return encodeDeterministic(t, claims)
}

// specCertificateV3_1 wraps a CWT payload into the spec's COSE_Sign1: ES256
// over an RFC 9162 tree in the protected header, one inclusion proof in the
// unprotected one, and a placeholder signature.
func specCertificateV3_1(t *testing.T, payload []byte) []byte {
	t.Helper()

	protectedHeader := encodeDeterministic(t, map[int]int{
		1:             coseAlgorithmES256,
		coseHeaderVDS: coseVDSRFC9162SHA256,
	})
	unprotectedHeader := encodeDeterministic(t, map[int]any{
		coseHeaderVDP: map[int]any{coseVDPInclusionProofs: [][]byte{testInclusionProofV3_1}},
	})

	return encodeDeterministic(t, []any{
		protectedHeader,
		cbor.RawMessage(unprotectedHeader),
		payload,
		make([]byte, coseES256SignatureLen),
	})
}

func specCertificateWithV3_1(t *testing.T, options specCertificateOptionsV3_1) []byte {
	t.Helper()

	protectedHeader := options.protectedHeader
	if protectedHeader == nil {
		protectedHeader = map[int]int{1: coseAlgorithmES256, coseHeaderVDS: coseVDSRFC9162SHA256}
	}
	unprotectedHeader := options.unprotectedHeader
	if unprotectedHeader == nil {
		unprotectedHeader = map[int]any{
			coseHeaderVDP: map[int]any{coseVDPInclusionProofs: [][]byte{testInclusionProofV3_1}},
		}
	}
	signature := options.signature
	if signature == nil {
		signature = make([]byte, coseES256SignatureLen)
	}

	return encodeDeterministic(t, []any{
		encodeDeterministic(t, protectedHeader),
		cbor.RawMessage(encodeDeterministic(t, unprotectedHeader)),
		specCertificatePayloadWithV3_1(t, options),
		signature,
	})
}

// encodeDeterministic encodes per RFC 8949 section 4.2.1, as the spec requires
// and the vendor CLI does, so the fixtures are the bytes a ceremony would
// store.
func encodeDeterministic(t *testing.T, value any) []byte {
	t.Helper()

	encoder, err := cbor.CoreDetEncOptions().EncMode()
	require.NoError(t, err)
	encoded, err := encoder.Marshal(value)
	require.NoError(t, err)
	return encoded
}
