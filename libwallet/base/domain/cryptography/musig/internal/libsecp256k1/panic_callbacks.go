package libsecp256k1

// This file turns libsecp256k1 faults into Go panics. The library aborts the
// process by default, which on mobile kills the app without a word about why.
// An illegal argument means this package called the library wrong and an error
// means the library itself failed, so neither is recoverable by the caller: the
// panic is there to be reported, not caught. cgo unwinds it back through the C
// frames the callback was called from.

import "C"

import (
	"unsafe"

	"github.com/go-errors/errors"
)

//export secp256k1PanicIllegal
func secp256k1PanicIllegal(message *C.char, _ unsafe.Pointer) {
	panic(errors.Errorf("libsecp256k1 illegal argument: %s", C.GoString(message)))
}

//export secp256k1PanicError
func secp256k1PanicError(message *C.char, _ unsafe.Pointer) {
	panic(errors.Errorf("libsecp256k1 internal error: %s", C.GoString(message)))
}
