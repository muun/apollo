package musig

// #include <stdlib.h>
// #include "umbrella.h"
// #cgo CFLAGS: -DECMULT_WINDOW_SIZE=15 -DECMULT_GEN_PREC_BITS=4 -DSECP256K1_BUILD
import "C"
import (
	"bytes"
	"crypto/rand"
	"fmt"
	"unsafe"

	"github.com/btcsuite/btcd/btcec"
	"github.com/muun/libwallet/btcsuitew/chainhashw"
)

func toUchar(buf []byte) *C.uchar {
	// See https://stackoverflow.com/a/51428826 on why this is needed
	var bufptr *byte
	if cap(buf) > 0 {
		bufptr = &(buf[:1][0])
	}
	return (*C.uchar)(bufptr)
}

var ctx *C.struct_secp256k1_context_struct

func init() {
	ctx = C.secp256k1_context_create(C.SECP256K1_CONTEXT_SIGN | C.SECP256K1_CONTEXT_VERIFY)
	// TODO: consider using secp256k1_context_set_illegal_callback
}

func CombinePubKeysWithTweak(userKey, muunKey *btcec.PublicKey, customTweak []byte) (*btcec.PublicKey, error) {
	combined, err := combinePubKeys(userKey, muunKey)
	if err != nil {
		return nil, err
	}

	tweak, err := tagTweakOrDefault(combined, customTweak)
	if err != nil {
		return nil, err
	}

	var tweakPubKey C.secp256k1_pubkey
	if C.secp256k1_xonly_pubkey_tweak_add(
		ctx,
		&tweakPubKey,
		combined,
		toUchar(tweak[:]),
	) == 0 {
		return nil, fmt.Errorf("failed to tweak key")
	}

	var serialized [33]byte
	var serializedSize C.size_t
	serializedSize = 33
	if C.secp256k1_ec_pubkey_serialize(
		ctx,
		toUchar(serialized[:]),
		&serializedSize,
		&tweakPubKey,
		C.SECP256K1_EC_COMPRESSED,
	) == 0 {
		return nil, fmt.Errorf("failed to serialize tweaked key")
	}

	return btcec.ParsePubKey(serialized[:], btcec.S256())
}

func combinePubKeys(userKey *btcec.PublicKey, muunKey *btcec.PublicKey) (*C.secp256k1_xonly_pubkey, error) {

	// Safe C-interop rules require C pointer (ie the array) can't contain go
	// pointers. These go into an array, so we need to allocate manually.
	userXOnly := (*C.secp256k1_xonly_pubkey)(C.malloc(C.sizeof_secp256k1_xonly_pubkey))
	defer C.free(unsafe.Pointer(userXOnly))

	muunXOnly := (*C.secp256k1_xonly_pubkey)(C.malloc(C.sizeof_secp256k1_xonly_pubkey))
	defer C.free(unsafe.Pointer(muunXOnly))

	if C.secp256k1_xonly_pubkey_parse(ctx, userXOnly, toUchar(userKey.SerializeCompressed()[1:])) == 0 {
		return nil, fmt.Errorf("failed to parse user key")
	}

	if C.secp256k1_xonly_pubkey_parse(ctx, muunXOnly, toUchar(muunKey.SerializeCompressed()[1:])) == 0 {
		return nil, fmt.Errorf("failed to parse muun key")
	}

	keys := []*C.secp256k1_xonly_pubkey{
		userXOnly,
		muunXOnly,
	}

	var combined C.secp256k1_xonly_pubkey
	if C.secp256k1_musig_pubkey_agg(
		ctx,
		nil,
		&combined,
		nil,
		(**C.secp256k1_xonly_pubkey)(&keys[:1][0]),
		2,
	) == 0 {
		return nil, fmt.Errorf("failed to combne keys")
	}

	return &combined, nil
}

// RandomSessionId returns a safe random session id. Session IDs must not be
// repeated otherwise private keys are compromised.
func RandomSessionId() [32]byte {
	var buf [32]byte
	_, err := rand.Read(buf[:])
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

// GeneratePubNonce returns the pub nonce for a given session id
func GeneratePubNonce(sessionId [32]byte) [66]byte {

	var secnonce C.secp256k1_musig_secnonce
	var pubNonce C.secp256k1_musig_pubnonce

	res := C.secp256k1_musig_nonce_gen(
		ctx,
		&secnonce,
		&pubNonce,
		toUchar(sessionId[:]),
		nil,
		nil,
		nil,
		nil,
	)

	if res == 0 {
		panic("failed to generate nonce")
	}

	var pubNonceBytes [66]byte
	res = C.secp256k1_musig_pubnonce_serialize(
		ctx,
		toUchar(pubNonceBytes[:]),
		&pubNonce,
	)
	if res == 0 {
		panic("failed to serialize pub nonce")
	}

	return pubNonceBytes
}

// AddUserSignatureAndCombine with partial muun signature.
func AddUserSignatureAndCombine(
	data [32]byte,
	userKey *btcec.PrivateKey,
	muunKey *btcec.PublicKey,
	rawMuunPartialSig [32]byte,
	rawMuunPubNonce [66]byte,
	sessionId [32]byte,
	customTweak []byte,
) ([64]byte, error) {

	var signature [64]byte

	// Safe C-interop rules require C pointer (ie the array) can't contain go
	// pointers. These go into an array, so we need to allocate manually.
	userXOnly := (*C.secp256k1_xonly_pubkey)(C.malloc(C.sizeof_secp256k1_xonly_pubkey))
	defer C.free(unsafe.Pointer(userXOnly))

	muunXOnly := (*C.secp256k1_xonly_pubkey)(C.malloc(C.sizeof_secp256k1_xonly_pubkey))
	defer C.free(unsafe.Pointer(muunXOnly))

	if C.secp256k1_xonly_pubkey_parse(
		ctx,
		userXOnly,
		toUchar(userKey.PubKey().SerializeCompressed()[1:]),
	) == 0 {
		return signature, fmt.Errorf("failed to make xonly from user key")
	}

	if C.secp256k1_xonly_pubkey_parse(
		ctx,
		muunXOnly,
		toUchar(muunKey.SerializeCompressed()[1:]),
	) == 0 {
		return signature, fmt.Errorf("failed to make xonly from user key")
	}

	keys := []*C.secp256k1_xonly_pubkey{
		userXOnly,
		muunXOnly,
	}

	var combined C.secp256k1_xonly_pubkey
	var keyaggCache C.secp256k1_musig_keyagg_cache
	if C.secp256k1_musig_pubkey_agg(
		ctx,
		nil,
		&combined,
		&keyaggCache,
		(**C.secp256k1_xonly_pubkey)(&keys[:1][0]),
		2,
	) == 0 {
		return signature, fmt.Errorf("failed to combine keys")
	}

	var secnonce C.secp256k1_musig_secnonce
	userPubNonce := (*C.secp256k1_musig_pubnonce)(C.malloc(C.sizeof_secp256k1_musig_pubnonce))
	defer C.free(unsafe.Pointer(userPubNonce))

	if C.secp256k1_musig_nonce_gen(
		ctx,
		&secnonce,
		userPubNonce,
		toUchar(sessionId[:]),
		nil,
		nil,
		nil,
		nil,
	) == 0 {
		return signature, fmt.Errorf("failed to generate user nonce")
	}

	muunPubNonce := (*C.secp256k1_musig_pubnonce)(C.malloc(C.sizeof_secp256k1_musig_pubnonce))
	defer C.free(unsafe.Pointer(muunPubNonce))

	if C.secp256k1_musig_pubnonce_parse(
		ctx,
		muunPubNonce,
		toUchar(rawMuunPubNonce[:]),
	) == 0 {
		return signature, fmt.Errorf("failed to parse muun pub nonce")
	}

	tweak, err := tagTweakOrDefault(&combined, customTweak)
	if err != nil {
		return signature, err
	}

	var tweakedPubKey C.secp256k1_pubkey
	if C.secp256k1_musig_pubkey_tweak_add(
		ctx,
		&tweakedPubKey,
		toUchar(tweak[:]),
		&keyaggCache,
	) == 0 {
		return signature, fmt.Errorf("failed to tweak key")
	}

	// The API is kinda unhappy, and now requires us to transform the
	// tweaked pub key to x-only and overwrite the previous combined key
	if C.secp256k1_xonly_pubkey_from_pubkey(
		ctx,
		&combined,
		nil,
		&tweakedPubKey,
	) == 0 {
		return signature, fmt.Errorf("failed to transform tweaked key to xonly")
	}

	var aggNonce C.secp256k1_musig_aggnonce

	pubNonces := []*C.secp256k1_musig_pubnonce{
		userPubNonce,
		muunPubNonce,
	}
	if C.secp256k1_musig_nonce_agg(
		ctx,
		&aggNonce,
		(**C.secp256k1_musig_pubnonce)(&pubNonces[:1][0]),
		2,
	) == 0 {
		return signature, fmt.Errorf("failed to aggregate nonces")
	}

	var session C.secp256k1_musig_session

	if C.secp256k1_musig_nonce_process(
		ctx,
		&session,
		&aggNonce,
		toUchar(data[:]),
		&keyaggCache,
		nil,
	) == 0 {
		return signature, fmt.Errorf("failed to process nonces")
	}

	// Heap allocated since it will go in an array soon
	muunPartialSig := (*C.secp256k1_musig_partial_sig)(
		C.malloc(C.sizeof_secp256k1_musig_partial_sig),
	)
	defer C.free(unsafe.Pointer(muunPartialSig))

	if C.secp256k1_musig_partial_sig_parse(
		ctx,
		muunPartialSig,
		toUchar(rawMuunPartialSig[:]),
	) == 0 {
		return signature, fmt.Errorf("failed to parse muun partial sig")
	}

	if C.secp256k1_musig_partial_sig_verify(
		ctx,
		muunPartialSig,
		pubNonces[1],
		muunXOnly,
		&keyaggCache,
		&session,
	) == 0 {
		return signature, fmt.Errorf("partial sig is invalid")
	}

	var userKeyPair C.secp256k1_keypair
	if C.secp256k1_keypair_create(
		ctx,
		&userKeyPair,
		toUchar(userKey.Serialize()),
	) == 0 {
		return signature, fmt.Errorf("failed to create user key pair")
	}

	// Heap allocated since it will go in an array soon
	userPartialSig := (*C.secp256k1_musig_partial_sig)(
		C.malloc(C.sizeof_secp256k1_musig_partial_sig),
	)
	defer C.free(unsafe.Pointer(userPartialSig))

	if C.secp256k1_musig_partial_sign(
		ctx,
		userPartialSig,
		&secnonce,
		&userKeyPair,
		&keyaggCache,
		&session,
	) == 0 {
		return signature, fmt.Errorf("failed to sign with user key")
	}

	partialSigs := []*C.secp256k1_musig_partial_sig{
		userPartialSig, muunPartialSig,
	}

	if C.secp256k1_musig_partial_sig_agg(
		ctx,
		toUchar(signature[:]),
		&session,
		(**C.secp256k1_musig_partial_sig)(&partialSigs[:1][0]),
		2,
	) == 0 {
		return signature, fmt.Errorf("failed to combine signatures")
	}

	return signature, nil
}

func ComputeMuunPartialSignature(
	data [32]byte,
	userKey *btcec.PublicKey,
	muunKey *btcec.PrivateKey,
	rawUserPubNonce [66]byte,
	sessionId [32]byte,
	customTweak []byte,
) ([32]byte, error) {
	var rawPartialMuunSig [32]byte

	// Safe C-interop rules require C pointer (ie the array) can't contain go
	// pointers. These go into an array, so we need to allocate manually.
	userXOnly := (*C.secp256k1_xonly_pubkey)(
		C.malloc(C.sizeof_secp256k1_xonly_pubkey),
	)
	defer C.free(unsafe.Pointer(userXOnly))

	muunXOnly := (*C.secp256k1_xonly_pubkey)(
		C.malloc(C.sizeof_secp256k1_xonly_pubkey),
	)
	defer C.free(unsafe.Pointer(muunXOnly))

	if C.secp256k1_xonly_pubkey_parse(
		ctx,
		userXOnly,
		toUchar(userKey.SerializeCompressed()[1:]),
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to make xonly from user key")
	}

	if C.secp256k1_xonly_pubkey_parse(
		ctx,
		muunXOnly,
		toUchar(muunKey.PubKey().SerializeCompressed()[1:]),
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to make xonly from user key")
	}

	keys := []*C.secp256k1_xonly_pubkey{
		userXOnly,
		muunXOnly,
	}

	var combined C.secp256k1_xonly_pubkey
	var keyaggCache C.secp256k1_musig_keyagg_cache
	if C.secp256k1_musig_pubkey_agg(
		ctx,
		nil,
		&combined,
		&keyaggCache,
		(**C.secp256k1_xonly_pubkey)(&keys[:1][0]),
		2,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to combine keys")
	}

	var secnonce C.secp256k1_musig_secnonce
	muunPubNonce := (*C.secp256k1_musig_pubnonce)(C.malloc(C.sizeof_secp256k1_musig_pubnonce))

	if C.secp256k1_musig_nonce_gen(
		ctx,
		&secnonce,
		muunPubNonce,
		toUchar(sessionId[:]),
		nil,
		nil,
		nil,
		nil,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to create pre session")
	}

	userPubNonce := (*C.secp256k1_musig_pubnonce)(C.malloc(C.sizeof_secp256k1_musig_pubnonce))
	defer C.free(unsafe.Pointer(userPubNonce))

	if C.secp256k1_musig_pubnonce_parse(
		ctx,
		userPubNonce,
		toUchar(rawUserPubNonce[:]),
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to parse muun pub nonce")
	}

	tweak, err := tagTweakOrDefault(&combined, customTweak)
	if err != nil {
		return rawPartialMuunSig, err
	}

	var tweakedPubKey C.secp256k1_pubkey
	if C.secp256k1_musig_pubkey_tweak_add(
		ctx,
		&tweakedPubKey,
		toUchar(tweak[:]),
		&keyaggCache,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to tweak key")
	}

	// The API is kinda unhappy, and now requires us to transform the
	// tweaked pub key to x-only and overwrite the previous combined key
	if C.secp256k1_xonly_pubkey_from_pubkey(
		ctx,
		&combined,
		nil,
		&tweakedPubKey,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to transform tweaked key to xonly")
	}

	var aggNonce C.secp256k1_musig_aggnonce

	pubNonces := []*C.secp256k1_musig_pubnonce{
		userPubNonce,
		muunPubNonce,
	}
	if C.secp256k1_musig_nonce_agg(
		ctx,
		&aggNonce,
		(**C.secp256k1_musig_pubnonce)(&pubNonces[:1][0]),
		2,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to aggregate nonces")
	}

	var session C.secp256k1_musig_session

	if C.secp256k1_musig_nonce_process(
		ctx,
		&session,
		&aggNonce,
		toUchar(data[:]),
		&keyaggCache,
		nil,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to process nonces")
	}

	var muunKeyPair C.secp256k1_keypair
	if C.secp256k1_keypair_create(
		ctx,
		&muunKeyPair,
		toUchar(muunKey.Serialize()),
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to create user key pair")
	}

	var muunPartialSig C.secp256k1_musig_partial_sig
	if C.secp256k1_musig_partial_sign(
		ctx,
		&muunPartialSig,
		&secnonce,
		&muunKeyPair,
		&keyaggCache,
		&session,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to sign with muun key")
	}

	// Here to catch bugs!
	if C.secp256k1_musig_partial_sig_verify(
		ctx,
		&muunPartialSig,
		muunPubNonce,
		muunXOnly,
		&keyaggCache,
		&session,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("partial sig is invalid")
	}

	if C.secp256k1_musig_partial_sig_serialize(
		ctx,
		toUchar(rawPartialMuunSig[:]),
		&muunPartialSig,
	) == 0 {
		return rawPartialMuunSig, fmt.Errorf("failed to serialize partial sig")
	}

	return rawPartialMuunSig, nil
}

// VerifySignature checks a Schnorr signature.
func VerifySignature(data [32]byte, signature [64]byte, pubKey *btcec.PublicKey) bool {
	var xOnly C.secp256k1_xonly_pubkey
	if C.secp256k1_xonly_pubkey_parse(
		ctx,
		&xOnly,
		toUchar(pubKey.SerializeCompressed()[1:]),
	) == 0 {
		return false
	}

	return C.secp256k1_schnorrsig_verify(
		ctx,
		toUchar(signature[:]),
		toUchar(data[:]),
		32,
		&xOnly,
	) == 1
}

func tagTweakOrDefault(pubKey *C.secp256k1_xonly_pubkey, customTweak []byte) ([32]byte, error) {
	var untaggedTweak []byte

	if customTweak != nil {
		if len(customTweak) != 32 {
			return [32]byte{}, fmt.Errorf("tweak must be 32 bytes long, not %d", len(customTweak))
		}

		var emptyTweak [32]byte
		if bytes.Equal(customTweak, emptyTweak[:]) {
			return [32]byte{}, fmt.Errorf("tweak can't be empty (zero-filled slice given)")
		}

		untaggedTweak = customTweak[:]

	} else {
		var serializedKey [32]byte
		if C.secp256k1_xonly_pubkey_serialize(
			ctx,
			toUchar(serializedKey[:]),
			pubKey,
		) == 0 {
			return [32]byte{}, fmt.Errorf("failed to serialize key to calculate default tweak")
		}

		untaggedTweak = serializedKey[:]
	}

	var taggedTweak [32]byte
	copy(taggedTweak[:], chainhashw.TaggedHashB(chainhashw.TagTapTweak, untaggedTweak))

	return taggedTweak, nil
}
