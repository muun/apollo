package model

import (
	"crypto/sha256"

	"github.com/go-errors/errors"
)

// V3 wire sizes, duplicated from the nfc package to avoid an import
// cycle (domain/nfc imports this package).
const (
	ServerPublicKeyLengthV3 = 65
	MACLengthV3             = 32
)

// clientApprovalTag opens what the client signs to approve a challenge, the
// way "card-approval" opens what the card signs.
const clientApprovalTag = "client-approval"

// SecurityCardSignChallengeV3 carries everything a card needs to
// verify and sign a challenge: the server's ephemeral key, the
// monotonic counter, the pairing index, the human-readable reason
// (bound into the MAC as its hash), and the server-computed MAC.
//
// The MAC covers SHA256(reason) rather than the reason itself, so that
// chunking a long one cannot change what the card is asked to approve.
type SecurityCardSignChallengeV3 struct {
	ServerPublicKey []byte // C (65 bytes) - server's ephemeral public key
	CallCounter     uint32 // strictly increasing for anti-replay
	Index           uint16 // pairing index PAIR handed out (always 0 on firmware 3.0)
	Reason          []byte // human-readable action description, hashed into the MAC
	MAC             []byte // HMAC over tag || C || call_counter || index || SHA256(reason)
}

// NewSecurityCardSignChallengeV3 builds a sign challenge, validating the
// wire-fixed field sizes. The fields come from the server, so violations
// return errors rather than panics.
func NewSecurityCardSignChallengeV3(
	serverPublicKey []byte,
	callCounter uint32,
	index uint16,
	reason []byte,
	mac []byte,
) (*SecurityCardSignChallengeV3, error) {
	if len(serverPublicKey) != ServerPublicKeyLengthV3 {
		return nil, errors.Errorf(
			"server public key must be %d bytes, got %d",
			ServerPublicKeyLengthV3,
			len(serverPublicKey),
		)
	}
	if len(mac) != MACLengthV3 {
		return nil, errors.Errorf("mac must be %d bytes, got %d", MACLengthV3, len(mac))
	}

	return &SecurityCardSignChallengeV3{
		ServerPublicKey: serverPublicKey,
		CallCounter:     callCounter,
		Index:           index,
		Reason:          reason,
		MAC:             mac,
	}, nil
}

// ClientApprovalDigest returns what the client signs to approve this
// challenge:
//
//	SHA256("client-approval" || C || SHA256(reason))
//
// These are the bytes the card signs to approve it, under the client's own
// tag. The spec leaves the hash open; BIP340 signs 32 bytes, so it is the
// one the card's ECDSA applies.
func (c *SecurityCardSignChallengeV3) ClientApprovalDigest() []byte {
	reasonHash := sha256.Sum256(c.Reason)

	toBeSigned := make([]byte, 0, len(clientApprovalTag)+len(c.ServerPublicKey)+sha256.Size)
	toBeSigned = append(toBeSigned, clientApprovalTag...)
	toBeSigned = append(toBeSigned, c.ServerPublicKey...)
	toBeSigned = append(toBeSigned, reasonHash[:]...)

	digest := sha256.Sum256(toBeSigned)
	return digest[:]
}
