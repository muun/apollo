package libwallet

import (
	"encoding/hex"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/musig"
)

type MusigNonces struct {
	sessionIds      [][32]byte
	publicNonces    [][66]byte
	addressVersions []int
}

func (m *MusigNonces) GetPubnonceHex(index int) string {
	return hex.EncodeToString(m.publicNonces[index][:])
}

// NOTE: this function only generates v040 nonces, used until GenerateNonce is fully adopted.
// after that this function should be deleted. Currently, this function is only used by gomobile
func GenerateMusigNonces(count int) *MusigNonces {
	sessionIds := make([][32]byte, 0)
	publicNonces := make([][66]byte, 0)
	addressVersions := make([]int, 0)

	for i := 0; i < count; i += 1 {
		sessionIds = append(sessionIds, musig.RandomSessionId())
		nonce, _ := musig.MuSig2GenerateNonce(
			musig.Musig2v040Muun,
			sessionIds[i][:],
			nil,
		)
		publicNonces = append(publicNonces, nonce.PubNonce)
		addressVersions = append(addressVersions, addresses.V5)
	}

	return &MusigNonces{
		sessionIds,
		publicNonces,
		addressVersions,
	}
}

func EmptyMusigNonces() *MusigNonces {
	sessionIds := make([][32]byte, 0)
	publicNonces := make([][66]byte, 0)
	addressVersions := make([]int, 0)

	return &MusigNonces{
		sessionIds,
		publicNonces,
		addressVersions,
	}
}

// Generates a nonce for a specific address version. Returns the index of the
// generated nonce and reallocates the arrays of the current MusigNonces.
func (nonces *MusigNonces) GenerateNonce(addressVersion int, signerPubKeySerialized []byte) (int, error) {
	sessionId := musig.RandomSessionId()

	return nonces.generateStaticNonce(addressVersion, signerPubKeySerialized, sessionId)
}

// PREFER GenerateNonce, this function exists for tests only.
//
// Generates a nonce for a specific address version. Returns the index of the
// generated nonce and reallocates the arrays of the current MusigNonces.
// The provided sessionId MUST NOT be reused, it MUST be used only once.
func (nonces *MusigNonces) generateStaticNonce(addressVersion int, signerPubKeySerialized []byte, sessionId [32]byte) (int, error) {
	musigVersion := addresses.MusigVersionForAddress(addressVersion)

	signerPubKey, err := musig.ParsePubKey(musigVersion, signerPubKeySerialized)
	if err != nil {
		return 0, err
	}

	nonce, err := musig.MuSig2GenerateNonce(
		musigVersion,
		sessionId[:],
		signerPubKey.SerializeCompressed(),
	)
	if err != nil {
		return 0, err
	}

	nonces.addressVersions = append(nonces.addressVersions, addressVersion)
	nonces.sessionIds = append(nonces.sessionIds, sessionId)
	nonces.publicNonces = append(nonces.publicNonces, nonce.PubNonce)

	return len(nonces.sessionIds) - 1, nil
}
