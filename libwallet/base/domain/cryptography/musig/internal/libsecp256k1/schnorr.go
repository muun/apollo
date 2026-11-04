package libsecp256k1

// #include "secp256k1.h"
// #include "secp256k1_extrakeys.h"
// #include "secp256k1_schnorrsig.h"
import "C"

import (
	"github.com/go-errors/errors"
)

// ErrInvalidSignature is returned when a well formed signature does not verify,
// as opposed to one that could not be read at all. Callers that report validity
// rather than failing need to tell the two apart.
var ErrInvalidSignature = errors.New("the signature is invalid")

// SignSchnorr signs a message with a single key, as BIP340 describes. The
// signature is deterministic: libsecp256k1 derives its nonce from the secret key
// and the message.
func SignSchnorr(message, secretKey []byte) ([]byte, error) {
	if len(message) != MessageSize {
		return nil, errors.Errorf(
			"message is %d bytes long, expected %d", len(message), MessageSize,
		)
	}

	if len(secretKey) != SecretKeySize {
		return nil, errors.Errorf(
			"secret key is %d bytes long, expected %d",
			len(secretKey), SecretKeySize,
		)
	}

	var keyPair C.secp256k1_keypair
	if C.secp256k1_keypair_create(ctx, &keyPair, toUchar(secretKey)) != 1 {
		return nil, errors.New("failed to build a key pair from the secret key")
	}

	signature := make([]byte, SignatureSize)
	if C.secp256k1_schnorrsig_sign32(
		ctx, toUchar(signature), toUchar(message), &keyPair, nil,
	) != 1 {
		return nil, errors.New("failed to sign the message")
	}

	return signature, nil
}

// VerifySignature checks a Schnorr signature against the x-only form of the key
// that should have produced it.
func VerifySignature(signature, message, xOnlyPublicKey []byte) error {
	if len(signature) != SignatureSize {
		return errors.Errorf(
			"signature is %d bytes long, expected %d",
			len(signature), SignatureSize,
		)
	}

	if len(message) != MessageSize {
		return errors.Errorf(
			"message is %d bytes long, expected %d", len(message), MessageSize,
		)
	}

	if len(xOnlyPublicKey) != XOnlyPublicKeySize {
		return errors.Errorf(
			"public key is %d bytes long, expected %d",
			len(xOnlyPublicKey), XOnlyPublicKeySize,
		)
	}

	var parsedKey C.secp256k1_xonly_pubkey
	if C.secp256k1_xonly_pubkey_parse(ctx, &parsedKey, toUchar(xOnlyPublicKey)) != 1 {
		return errors.New("failed to parse the public key")
	}

	if C.secp256k1_schnorrsig_verify(
		ctx,
		toUchar(signature),
		toUchar(message),
		C.size_t(len(message)),
		&parsedKey,
	) != 1 {
		return ErrInvalidSignature
	}

	return nil
}
