package libwallet

import (
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"

	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/legacy/addresses"
	"github.com/muun/libwallet/platform/secret"
)

type MusigNonces struct {
	sessionIDs      []secret.Bytes[[32]byte]
	publicNonces    [][66]byte
	addressVersions []int
}

func (nonces *MusigNonces) GetPubnonceHex(index int) string {
	return hex.EncodeToString(nonces.publicNonces[index][:])
}

func (nonces *MusigNonces) Length() int {
	return len(nonces.publicNonces)
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (nonces MusigNonces) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, nonces.LogValue().String())
}

// LogValue returns the number of nonces, without their session IDs.
func (nonces MusigNonces) LogValue() slog.Value {
	return slog.GroupValue(slog.Int("count", len(nonces.publicNonces)))
}

// NOTE: this function only generates v040 nonces, used until GenerateNonce is fully adopted.
// after that this function should be deleted. Currently, this function is only used by gomobile
func GenerateMusigNonces(count int) *MusigNonces {
	sessionIDs := make([]secret.Bytes[[32]byte], 0)
	publicNonces := make([][66]byte, 0)
	addressVersions := make([]int, 0)

	for i := 0; i < count; i += 1 {
		sessionID := musig.RandomSessionID()
		sessionIDs = append(sessionIDs, secret.NewBytes(sessionID))
		nonce, _ := musig.MuSig2GenerateNonce(
			musig.Musig2v040Muun,
			sessionID[:],
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
	sessionIDs := make([]secret.Bytes[[32]byte], 0)
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
func (nonces *MusigNonces) GenerateNonce(
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
	nonces.sessionIDs = append(nonces.sessionIDs, secret.NewBytes(sessionID))
	nonces.publicNonces = append(nonces.publicNonces, nonce.PubNonce)

	return len(nonces.sessionIDs) - 1, nil
}
