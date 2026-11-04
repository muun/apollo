// Package libsecp256k1 binds the MuSig2, Schnorr and key handling primitives of
// libsecp256k1. The library sources live under upstream/, copied verbatim by
// import.sh.
//
// Calls into C pass flat byte buffers, which cgo pins for the duration of the
// call on their own. Key aggregation is the exception, since libsecp256k1 takes
// the keys as an array of pointers: that array is pinned with runtime.Pinner
// instead of being allocated with C.malloc the way the pre-2024 bindings did,
// so the garbage collector keeps owning it and no error path has to free it.
package libsecp256k1

/*
#cgo CFLAGS: -I${SRCDIR}/upstream/include
#cgo CFLAGS: -DENABLE_MODULE_EXTRAKEYS=1 -DENABLE_MODULE_SCHNORRSIG=1 -DENABLE_MODULE_MUSIG=1
#cgo CFLAGS: -DECMULT_WINDOW_SIZE=15

#include "secp256k1.h"
#include "secp256k1_extrakeys.h"
#include "secp256k1_musig.h"
#include "secp256k1_schnorrsig.h"

typedef void (*callbackFunc)(const char *message, void *data);
extern void secp256k1PanicIllegal(const char *message, void *data);
extern void secp256k1PanicError(const char *message, void *data);
*/
import "C"

import (
	"crypto/rand"

	"github.com/go-errors/errors"
)

const (
	// PublicKeySize is the length of a public key in compressed form.
	PublicKeySize = 33

	// XOnlyPublicKeySize is the length of a public key without its parity bit.
	XOnlyPublicKeySize = 32

	// TweakSize is the length of a tweak applied to an aggregate key.
	TweakSize = 32

	// MessageSize is the length of the data a signature commits to.
	MessageSize = 32

	// SecretKeySize is the length of a signer's secret key.
	SecretKeySize = 32

	// SignatureSize is the length of a Schnorr signature.
	SignatureSize = 64
)

// ctx is the library context every call is made against. It holds the
// precomputed tables, is read-only once built and can be shared between
// goroutines.
var ctx *C.secp256k1_context

func init() {
	ctx = C.secp256k1_context_create(C.SECP256K1_CONTEXT_NONE)

	// Both callbacks abort the process by default, which on mobile means
	// killing the app without a word about why.
	C.secp256k1_context_set_illegal_callback(
		ctx,
		C.callbackFunc(C.secp256k1PanicIllegal), // the callback to install
		nil,                                     // extra argument the callback receives, unused
	)
	C.secp256k1_context_set_error_callback(
		ctx,
		C.callbackFunc(C.secp256k1PanicError), // the callback to install
		nil,                                   // extra argument the callback receives, unused
	)

	// secp256k1.h highly recommends randomizing the context before working
	// with secret keys: it blinds those computations against side channels.
	// It changes what the hardware leaks, not what any call returns: signing
	// stays deterministic.
	seed := make([]byte, 32)
	if _, err := rand.Read(seed); err != nil {
		panic(errors.Errorf("failed to read a context seed: %w", err))
	}

	if C.secp256k1_context_randomize(ctx, toUchar(seed)) != 1 {
		panic(errors.New("failed to randomize the context"))
	}
}

// toUchar points at the first byte of buf for C to read or write, without
// copying it. Capacity rather than length is what it needs: an empty slice that
// was allocated still yields a valid pointer, which matters because the library
// rejects a null argument even where it reads no bytes from it.
func toUchar(buf []byte) *C.uchar {
	// See https://stackoverflow.com/a/51428826 on why this is needed
	var bufptr *byte
	if cap(buf) > 0 {
		bufptr = &(buf[:1][0])
	}
	return (*C.uchar)(bufptr)
}

// serializePublicKey returns the compressed form of a parsed public key.
func serializePublicKey(publicKey *C.secp256k1_pubkey) ([]byte, error) {
	serialized := make([]byte, PublicKeySize)
	size := C.size_t(PublicKeySize)

	if C.secp256k1_ec_pubkey_serialize(
		ctx,
		toUchar(serialized),       // out: the serialized key
		&size,                     // in: the buffer size, out: how much was written
		publicKey,                 // the key to serialize
		C.SECP256K1_EC_COMPRESSED, // the format to serialize it in
	) != 1 {
		return nil, errors.New("failed to serialize public key")
	}

	return serialized, nil
}
