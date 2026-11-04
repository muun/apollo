package simulator

import (
	"bytes"
	"crypto/ecdh"
	"encoding/hex"

	"github.com/go-errors/errors"
)

// DEV ONLY — this file holds the Muun server identity keypair used by
// SimulatedHoustonService to simulate the V3 pairing flow (DH2 = cosignerPriv·P
// on the server side; the matching cosignerPub is baked into the V3 firmware
// and the V3 card mock). Production HoustonClient does not read these
// constants — they only exist so the local simulator can complete pairings
// against firmware that expects the real Muun identity binding.
//
// Values are byte-for-byte aligned with the firmware reference at
// cards/muuncardv3/main.go on the muuncard-proto-v3 branch so signatures
// and shared secrets match across components without manual coordination.

const cosignerPrivDevHex = "f22989f8d74a5da5c911c605df8a290c0bf89de794f17cc03c3a663f9eb010f8"

const cosignerPubDevHex = "04bb0ce9365239e80769f9101fbc6a0d346de17c839ef56e90419dc2c0d7b666270588cc9de355665ae3651c01898f0bc34415d2ddfa72a6c91842efbe99d83b4d" //nolint:lll, a hex key does not wrap

var cosignerPrivDevBytes = mustDecodeHexConst(cosignerPrivDevHex)

var cosignerPubDevBytes = mustDecodeHexConst(cosignerPubDevHex)

// CosignerPubDev returns the Muun identity public key this simulator pairs with,
// the one a card mock has to be built against.
func CosignerPubDev() []byte {
	return bytes.Clone(cosignerPubDevBytes)
}

func mustDecodeHexConst(s string) []byte {
	b, err := hex.DecodeString(s)
	if err != nil {
		panic(errors.Errorf("simulated houston dev key: invalid hex constant: %w", err))
	}
	return b
}

// init verifies cosignerPriv and cosignerPub are a matching keypair. Catches the
// case where one constant is rotated without the other.
func init() {
	privKey, err := ecdh.P256().NewPrivateKey(cosignerPrivDevBytes)
	if err != nil {
		panic(errors.Errorf("simulated houston dev key: invalid cosignerPriv: %w", err))
	}
	derivedPub := privKey.PublicKey().Bytes()
	expectedPub := cosignerPubDevBytes
	if !bytes.Equal(derivedPub, expectedPub) {
		panic(errors.Errorf(
			"simulated houston dev key: cosignerPriv does not derive to cosignerPub",
		))
	}
}
