package librs

/*
#cgo linux,!android,arm64  LDFLAGS: ${SRCDIR}/libs/aarch64-unknown-linux-musl-librs.a
#cgo linux,!android,amd64  LDFLAGS: ${SRCDIR}/libs/x86_64-unknown-linux-musl-librs.a
#cgo linux,!android,386    LDFLAGS: ${SRCDIR}/libs/i686-unknown-linux-musl-librs.a

#cgo android,arm64 LDFLAGS: ${SRCDIR}/libs/aarch64-linux-android-librs.a
#cgo android,arm LDFLAGS: ${SRCDIR}/libs/armv7-linux-androideabi-librs.a
#cgo android,386 LDFLAGS: ${SRCDIR}/libs/i686-linux-android-librs.a
#cgo android,amd64 LDFLAGS: ${SRCDIR}/libs/x86_64-linux-android-librs.a

#cgo darwin,arm64 LDFLAGS: ${SRCDIR}/libs/aarch64-apple-darwin-librs.a
#cgo darwin,amd64 LDFLAGS: ${SRCDIR}/libs/x86_64-apple-darwin-librs.a

#cgo ios LDFLAGS: ${SRCDIR}/libs/multi-apple-ios-librs.a

#cgo windows,386 LDFLAGS: ${SRCDIR}/libs/i686-pc-windows-gnu-librs.a
#cgo windows,amd64 LDFLAGS: ${SRCDIR}/libs/x86_64-pc-windows-gnu-librs.a
// Rust stdlib requires nt.dll which is not linked by default by mignw
#cgo windows LDFLAGS: -lntdll

#include "librs.h"
#include <stdlib.h>
*/
import "C"
import "unsafe"

type CCharArray struct {
	array C.CharArray
}

func make_char_array(value []byte) CCharArray {
	ptr := C.malloc(C.size_t(len(value)))
	for i := 0; i < len(value); i++ {
		*(*byte)(unsafe.Add(ptr, i)) = value[i]
	}
	return CCharArray{
		array: C.CharArray{
			data: (*C.char)(ptr),
			len:  C.uint64_t(len(value)),
		},
	}
}

func free_char_array(array CCharArray) {
	C.free(unsafe.Pointer(array.array.data))
}

func extract_value(array CCharArray) []byte {
	res := make([]byte, int(array.array.len))
	for i := 0; i < int(array.array.len); i++ {
		res[i] = *(*byte)(unsafe.Add(unsafe.Pointer(array.array.data), i))
	}
	return res
}

func Plonky2ServerKeyVerify(
	proof []byte,
	recovery_code_public_key []byte,
	hpke_ephemeral_public_key []byte,
	ciphertext []byte,
	plaintext_public_key []byte,
) []byte {

	proof_char_array := make_char_array(proof)
	defer free_char_array(proof_char_array)

	recovery_code_public_key_char_array := make_char_array(recovery_code_public_key)
	defer free_char_array(recovery_code_public_key_char_array)

	hpke_ephemeral_public_key_char_array := make_char_array(hpke_ephemeral_public_key)
	defer free_char_array(hpke_ephemeral_public_key_char_array)

	ciphertext_char_array := make_char_array(ciphertext)
	defer free_char_array(ciphertext_char_array)

	plaintext_public_key_char_array := make_char_array(plaintext_public_key)
	defer free_char_array(plaintext_public_key_char_array)

	result_char_array := CCharArray{array: C.plonky2_server_key_verify(
		proof_char_array.array,
		recovery_code_public_key_char_array.array,
		hpke_ephemeral_public_key_char_array.array,
		ciphertext_char_array.array,
		plaintext_public_key_char_array.array,
	)}
	defer free_char_array(result_char_array)

	return extract_value(result_char_array)
}