package libwallet

import (
	"math/big"
)

func paddedSerializeBigInt(size uint, x *big.Int) []byte {
	src := x.Bytes()
	dst := make([]byte, 0, size)

	for i := 0; i < int(size)-len(src); i++ {
		dst = append(dst, 0)
	}

	return append(dst, src...)
}
