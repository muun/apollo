package libsecp256k1

// #include "secp256k1.h"
// #include "secp256k1_musig.h"
import "C"

import (
	"runtime"

	"github.com/go-errors/errors"
)

const (
	// SessionIDSize is the length of the session id a nonce is derived from.
	SessionIDSize = 32

	// PublicNonceSize is the length of a serialized public nonce, which holds
	// two points.
	PublicNonceSize = 66
)

// GeneratePublicNonce derives a signer's nonce from a session id and the
// signer's compressed public key, and returns the half that is shared with the
// other signers. The secret half is not returned: signing derives its own from
// the same session id, so it never outlives a single call.
//
// The session id must be uniformly random and must never be reused, since two
// signatures made with the same one leak the secret key.
func GeneratePublicNonce(sessionID, publicKey []byte) ([]byte, error) {
	if len(sessionID) != SessionIDSize {
		return nil, errors.Errorf(
			"session id is %d bytes long, expected %d",
			len(sessionID), SessionIDSize,
		)
	}

	if len(publicKey) != PublicKeySize {
		return nil, errors.Errorf(
			"public key is %d bytes long, expected %d",
			len(publicKey), PublicKeySize,
		)
	}

	var parsedKey C.secp256k1_pubkey
	if C.secp256k1_ec_pubkey_parse(
		ctx, &parsedKey, toUchar(publicKey), C.size_t(len(publicKey)),
	) != 1 {
		return nil, errors.New("failed to parse the public key")
	}

	var secret C.secp256k1_musig_secnonce
	var public C.secp256k1_musig_pubnonce

	if err := generateNonce(sessionID, &parsedKey, &secret, &public); err != nil {
		return nil, err
	}

	serialized := make([]byte, PublicNonceSize)
	if C.secp256k1_musig_pubnonce_serialize(ctx, toUchar(serialized), &public) != 1 {
		return nil, errors.New("failed to serialize the public nonce")
	}

	return serialized, nil
}

// generateNonce derives both halves of a signer's nonce.
func generateNonce(
	sessionID []byte,
	publicKey *C.secp256k1_pubkey,
	secret *C.secp256k1_musig_secnonce,
	public *C.secp256k1_musig_pubnonce,
) error {

	// Nonce generation zeroes the session id it is given, to keep a caller from
	// deriving a second nonce from it, so it never sees the caller's copy.
	consumedSessionID := make([]byte, SessionIDSize)
	copy(consumedSessionID, sessionID)

	// The optional arguments harden the nonce against a weak session id, and the
	// round that only publishes the public nonce has none of them.
	if C.secp256k1_musig_nonce_gen(
		ctx,
		secret,                     // out: secret nonce
		public,                     // out: public nonce
		toUchar(consumedSessionID), // session id, zeroed by the call
		nil,                        // signer's private key
		publicKey,                  // signer's public key
		nil,                        // message to be signed
		nil,                        // key aggregation cache
		nil,                        // extra entropy
	) != 1 {
		return errors.New("failed to generate a nonce")
	}

	return nil
}

// AggregateNonces sums the public nonces of every signer into the one nonce the
// session signs against.
func AggregateNonces(publicNonces [][]byte) ([]byte, error) {
	if len(publicNonces) == 0 {
		return nil, errors.New("no public nonces to aggregate")
	}

	parsedNonces := make([]C.secp256k1_musig_pubnonce, len(publicNonces))
	pointedNonces := make([]*C.secp256k1_musig_pubnonce, len(publicNonces))

	// As in AggregatePublicKeys, C is handed an array of pointers into pinned
	// Go memory.
	var pinner runtime.Pinner
	defer pinner.Unpin()

	pinner.Pin(&parsedNonces[0])

	for i, publicNonce := range publicNonces {
		if len(publicNonce) != PublicNonceSize {
			return nil, errors.Errorf(
				"public nonce %d is %d bytes long, expected %d",
				i, len(publicNonce), PublicNonceSize,
			)
		}

		if C.secp256k1_musig_pubnonce_parse(
			ctx, &parsedNonces[i], toUchar(publicNonce),
		) != 1 {
			return nil, errors.Errorf("failed to parse public nonce %d", i)
		}

		pointedNonces[i] = &parsedNonces[i]
	}

	var aggregate C.secp256k1_musig_aggnonce
	if C.secp256k1_musig_nonce_agg(
		ctx, &aggregate, &pointedNonces[0], C.size_t(len(publicNonces)),
	) != 1 {
		return nil, errors.Errorf(
			"failed to aggregate %d public nonces", len(publicNonces),
		)
	}

	serialized := make([]byte, PublicNonceSize)
	if C.secp256k1_musig_aggnonce_serialize(
		ctx, toUchar(serialized), &aggregate,
	) != 1 {
		return nil, errors.New("failed to serialize the aggregate nonce")
	}

	return serialized, nil
}
