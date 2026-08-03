package libwallet

import (
	"encoding/hex"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/musig"
)

type MusigNonces struct {
	sessionIDs      [][32]byte
	publicNonces    [][66]byte
	addressVersions []int
}

func (m *MusigNonces) GetPubnonceHex(index int) string {
	return hex.EncodeToString(m.publicNonces[index][:])
}

func (m *MusigNonces) Length() int {
	return len(m.publicNonces)
}

// NOTE: this function only generates v040 nonces, used until GenerateNonce is fully adopted.
// after that this function should be deleted. Currently, this function is only used by gomobile
func GenerateMusigNonces(count int) *MusigNonces {
	sessionIDs := make([][32]byte, 0)
	publicNonces := make([][66]byte, 0)
	addressVersions := make([]int, 0)

	for i := 0; i < count; i += 1 {
		sessionIDs = append(sessionIDs, musig.RandomSessionID())
		nonce, _ := musig.MuSig2GenerateNonce(
			musig.Musig2v040Muun,
			sessionIDs[i][:],
			nil,
		)
		publicNonces = append(publicNonces, nonce.PubNonce)
		addressVersions = append(addressVersions, addresses.V5)
	}

	return &MusigNonces{
		sessionIDs,
		publicNonces,
		addressVersions,
	}
}

func EmptyMusigNonces() *MusigNonces {
	sessionIDs := make([][32]byte, 0)
	publicNonces := make([][66]byte, 0)
	addressVersions := make([]int, 0)

	return &MusigNonces{
		sessionIDs,
		publicNonces,
		addressVersions,
	}
}

// Generates a nonce for a specific address version. Returns the index of the
// generated nonce and reallocates the arrays of the current MusigNonces.
func (nonces *MusigNonces) GenerateNonce( //nolint:staticcheck // TODO: methods on the same type should have the same receiver name (seen 2x "m", 2x "nonces")
	addressVersion int,
	signerPubKeySerialized []byte,
) (int, error) {
	sessionID := musig.RandomSessionID()

	return nonces.generateStaticNonce(addressVersion, signerPubKeySerialized, sessionID)
}

// PREFER GenerateNonce, this function exists for tests only.
//
// Generates a nonce for a specific address version. Returns the index of the
// generated nonce and reallocates the arrays of the current MusigNonces.
// The provided sessionID MUST NOT be reused, it MUST be used only once.
func (nonces *MusigNonces) generateStaticNonce(
	addressVersion int,
	signerPubKeySerialized []byte,
	sessionID [32]byte,
) (int, error) {
	musigVersion := addresses.MusigVersionForAddress(addressVersion)

	signerPubKey, err := musig.ParsePubKey(musigVersion, signerPubKeySerialized)
	if err != nil {
		return 0, err
	}

	nonce, err := musig.MuSig2GenerateNonce(
		musigVersion,
		sessionID[:],
		signerPubKey.SerializeCompressed(),
	)
	if err != nil {
		return 0, err
	}

	nonces.addressVersions = append(nonces.addressVersions, addressVersion)
	nonces.sessionIDs = append(nonces.sessionIDs, sessionID)
	nonces.publicNonces = append(nonces.publicNonces, nonce.PubNonce)

	return len(nonces.sessionIDs) - 1, nil
}
