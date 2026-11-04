package libsecp256k1

// #include "secp256k1.h"
// #include "secp256k1_extrakeys.h"
// #include "secp256k1_musig.h"
import "C"

import (
	"runtime"

	"github.com/go-errors/errors"
)

// PartialSignatureSize is the length of one signer's partial signature.
const PartialSignatureSize = 32

// KeyAggregation is the aggregate of a set of signer public keys, along with
// every tweak applied to it. Signing needs the same aggregation the address was
// derived from, so it is carried from key aggregation through to the partial
// signature.
type KeyAggregation struct {
	cache C.secp256k1_musig_keyagg_cache
}

// AggregatePublicKeys aggregates the given compressed public keys, in the order
// they are given. Sorting them, which BIP327 leaves optional and MuSig2 v100
// applies, is the caller's job: the aggregate key depends on the order.
func AggregatePublicKeys(publicKeys [][]byte) (*KeyAggregation, error) {
	if len(publicKeys) == 0 {
		return nil, errors.New("no public keys to aggregate")
	}

	parsedKeys := make([]C.secp256k1_pubkey, len(publicKeys))
	pointedKeys := make([]*C.secp256k1_pubkey, len(publicKeys))

	// Aggregation takes an array of pointers to the parsed keys, and C may only
	// be handed Go memory holding Go pointers while those are pinned. One pin
	// covers the whole backing array.
	var pinner runtime.Pinner
	defer pinner.Unpin()

	pinner.Pin(&parsedKeys[0])

	for i, publicKey := range publicKeys {
		if len(publicKey) != PublicKeySize {
			return nil, errors.Errorf(
				"public key %d is %d bytes long, expected %d",
				i, len(publicKey), PublicKeySize,
			)
		}

		if C.secp256k1_ec_pubkey_parse(
			ctx,
			&parsedKeys[i],
			toUchar(publicKey),
			C.size_t(len(publicKey)),
		) != 1 {
			return nil, errors.Errorf("failed to parse public key %d", i)
		}

		pointedKeys[i] = &parsedKeys[i]
	}

	aggregation := &KeyAggregation{}
	if C.secp256k1_musig_pubkey_agg(
		ctx,
		nil,                       // out: aggregate key, read back from the cache
		&aggregation.cache,        // out: the key aggregation cache
		&pointedKeys[0],           // the keys to aggregate
		C.size_t(len(publicKeys)), // amount of keys
	) != 1 {
		return nil, errors.Errorf("failed to aggregate %d public keys", len(publicKeys))
	}

	return aggregation, nil
}

// PublicKey returns the aggregate public key in compressed form.
func (k *KeyAggregation) PublicKey() ([]byte, error) {
	var publicKey C.secp256k1_pubkey
	if C.secp256k1_musig_pubkey_get(ctx, &publicKey, &k.cache) != 1 {
		return nil, errors.New("failed to read the aggregate public key")
	}

	return serializePublicKey(&publicKey)
}

// AddPlainTweak mutates the aggregation, tweaking it by tweak * G, which is how
// unhardened BIP32 derivation is applied. A failed tweak leaves it unchanged.
func (k *KeyAggregation) AddPlainTweak(tweak []byte) error {
	if len(tweak) != TweakSize {
		return errors.Errorf(
			"tweak is %d bytes long, expected %d", len(tweak), TweakSize,
		)
	}

	if C.secp256k1_musig_pubkey_ec_tweak_add(
		ctx,
		nil,            // out: tweaked key, read back from the cache
		&k.cache,       // in/out: the key aggregation cache
		toUchar(tweak), // the tweak to apply
	) != 1 {
		return errors.New("failed to apply the plain tweak")
	}

	return nil
}

// AddXOnlyTweak mutates the aggregation the way taproot does, negating its key
// first if it has odd parity. A failed tweak leaves the aggregation unchanged.
func (k *KeyAggregation) AddXOnlyTweak(tweak []byte) error {
	if len(tweak) != TweakSize {
		return errors.Errorf(
			"tweak is %d bytes long, expected %d", len(tweak), TweakSize,
		)
	}

	if C.secp256k1_musig_pubkey_xonly_tweak_add(
		ctx,
		nil,            // out: tweaked key, read back from the cache
		&k.cache,       // in/out: the key aggregation cache
		toUchar(tweak), // the tweak to apply
	) != 1 {
		return errors.New("failed to apply the x-only tweak")
	}

	return nil
}

// Sign produces this signer's partial signature over message, for the
// session the given aggregate nonce belongs to. The signer's own nonce is
// derived from sessionID, which must be the one its public nonce was derived
// from. Signing two different messages with the same session id leaks the
// secret key.
func (k *KeyAggregation) Sign(
	message, aggregateNonce, secretKey, sessionID []byte,
) ([]byte, error) {

	if len(secretKey) != SecretKeySize {
		return nil, errors.Errorf(
			"secret key is %d bytes long, expected %d",
			len(secretKey), SecretKeySize,
		)
	}

	if len(sessionID) != SessionIDSize {
		return nil, errors.Errorf(
			"session id is %d bytes long, expected %d",
			len(sessionID), SessionIDSize,
		)
	}

	var keyPair C.secp256k1_keypair
	if C.secp256k1_keypair_create(ctx, &keyPair, toUchar(secretKey)) != 1 {
		return nil, errors.New("failed to build a key pair from the secret key")
	}

	var publicKey C.secp256k1_pubkey
	if C.secp256k1_keypair_pub(ctx, &publicKey, &keyPair) != 1 {
		return nil, errors.New("failed to read the public key of the key pair")
	}

	session, err := k.newSession(message, aggregateNonce)
	if err != nil {
		return nil, err
	}

	// The nonce is derived after every other step that can fail, so no error
	// return leaves a live secret nonce in memory.
	var secret C.secp256k1_musig_secnonce
	var public C.secp256k1_musig_pubnonce
	if err := generateNonce(sessionID, &publicKey, &secret, &public); err != nil {
		return nil, err
	}

	// Signing zeroes the secret nonce, so it cannot be signed with twice.
	var partialSignature C.secp256k1_musig_partial_sig
	if C.secp256k1_musig_partial_sign(
		ctx, &partialSignature, &secret, &keyPair, &k.cache, &session,
	) != 1 {
		return nil, errors.New("failed to compute the partial signature")
	}

	serialized := make([]byte, PartialSignatureSize)
	if C.secp256k1_musig_partial_sig_serialize(
		ctx, toUchar(serialized), &partialSignature,
	) != 1 {
		return nil, errors.New("failed to serialize the partial signature")
	}

	return serialized, nil
}

// VerifyPartialSignature checks one signer's partial signature against the
// public nonce and public key it claims to come from. It fails if the partial
// signature is not the one that signer should have produced for this session.
func (k *KeyAggregation) VerifyPartialSignature(
	message, aggregateNonce, partialSignature, publicNonce, publicKey []byte,
) error {

	if len(partialSignature) != PartialSignatureSize {
		return errors.Errorf(
			"partial signature is %d bytes long, expected %d",
			len(partialSignature), PartialSignatureSize,
		)
	}

	if len(publicNonce) != PublicNonceSize {
		return errors.Errorf(
			"public nonce is %d bytes long, expected %d",
			len(publicNonce), PublicNonceSize,
		)
	}

	if len(publicKey) != PublicKeySize {
		return errors.Errorf(
			"public key is %d bytes long, expected %d",
			len(publicKey), PublicKeySize,
		)
	}

	var parsedSignature C.secp256k1_musig_partial_sig
	if C.secp256k1_musig_partial_sig_parse(
		ctx, &parsedSignature, toUchar(partialSignature),
	) != 1 {
		return errors.New("failed to parse the partial signature")
	}

	var parsedNonce C.secp256k1_musig_pubnonce
	if C.secp256k1_musig_pubnonce_parse(ctx, &parsedNonce, toUchar(publicNonce)) != 1 {
		return errors.New("failed to parse the public nonce")
	}

	var parsedKey C.secp256k1_pubkey
	if C.secp256k1_ec_pubkey_parse(
		ctx, &parsedKey, toUchar(publicKey), C.size_t(len(publicKey)),
	) != 1 {
		return errors.New("failed to parse the public key")
	}

	session, err := k.newSession(message, aggregateNonce)
	if err != nil {
		return err
	}

	if C.secp256k1_musig_partial_sig_verify(
		ctx, &parsedSignature, &parsedNonce, &parsedKey, &k.cache, &session,
	) != 1 {
		return errors.New("the partial signature is invalid")
	}

	return nil
}

// CombineSignatures sums every signer's partial signature into the session's
// signature. Aggregation itself does not tell a good set of partial signatures
// from a bad one, so the result is verified against the aggregate key before
// being returned.
func (k *KeyAggregation) CombineSignatures(
	message, aggregateNonce []byte, partialSignatures [][]byte,
) ([]byte, error) {

	if len(partialSignatures) == 0 {
		return nil, errors.New("no partial signatures to combine")
	}

	parsedSignatures := make([]C.secp256k1_musig_partial_sig, len(partialSignatures))
	pointedSignatures := make([]*C.secp256k1_musig_partial_sig, len(partialSignatures))

	// As in AggregatePublicKeys, C is handed an array of pointers into pinned
	// Go memory.
	var pinner runtime.Pinner
	defer pinner.Unpin()

	pinner.Pin(&parsedSignatures[0])

	for i, partialSignature := range partialSignatures {
		if len(partialSignature) != PartialSignatureSize {
			return nil, errors.Errorf(
				"partial signature %d is %d bytes long, expected %d",
				i, len(partialSignature), PartialSignatureSize,
			)
		}

		if C.secp256k1_musig_partial_sig_parse(
			ctx, &parsedSignatures[i], toUchar(partialSignature),
		) != 1 {
			return nil, errors.Errorf("failed to parse partial signature %d", i)
		}

		pointedSignatures[i] = &parsedSignatures[i]
	}

	session, err := k.newSession(message, aggregateNonce)
	if err != nil {
		return nil, err
	}

	signature := make([]byte, SignatureSize)
	if C.secp256k1_musig_partial_sig_agg(
		ctx,
		toUchar(signature),
		&session,
		&pointedSignatures[0],
		C.size_t(len(partialSignatures)),
	) != 1 {
		return nil, errors.Errorf(
			"failed to combine %d partial signatures", len(partialSignatures),
		)
	}

	aggregateKey, err := k.PublicKey()
	if err != nil {
		return nil, err
	}

	// The aggregate key is compressed, and verification takes its x-only form.
	if err := VerifySignature(signature, message, aggregateKey[1:]); err != nil {
		return nil, err
	}

	return signature, nil
}

// newSession binds the aggregate nonce and the message to this aggregation,
// which is what every signer's partial signature is computed and checked
// against.
func (k *KeyAggregation) newSession(
	message, aggregateNonce []byte,
) (C.secp256k1_musig_session, error) {

	var session C.secp256k1_musig_session

	if len(message) != MessageSize {
		return session, errors.Errorf(
			"message is %d bytes long, expected %d", len(message), MessageSize,
		)
	}

	if len(aggregateNonce) != PublicNonceSize {
		return session, errors.Errorf(
			"aggregate nonce is %d bytes long, expected %d",
			len(aggregateNonce), PublicNonceSize,
		)
	}

	var parsedNonce C.secp256k1_musig_aggnonce
	if C.secp256k1_musig_aggnonce_parse(
		ctx, &parsedNonce, toUchar(aggregateNonce),
	) != 1 {
		return session, errors.New("failed to parse the aggregate nonce")
	}

	if C.secp256k1_musig_nonce_process(
		ctx,
		&session,         // out: the signing session
		&parsedNonce,     // the aggregate nonce of every signer
		toUchar(message), // the message to be signed
		&k.cache,         // the key aggregation cache
		nil,              // adaptor point, only for adaptor signatures
	) != 1 {
		return session, errors.New("failed to start the signing session")
	}

	return session, nil
}
