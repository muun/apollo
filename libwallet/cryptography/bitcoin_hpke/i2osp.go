package bitcoin_hpke

import "errors"

// Convert non-negative integer n to a w-length, big-endian byte string, as described in [RFC8017].
func i2Osp(n int, w int) []byte {
	// In our usage of i2Osp all input values are constants that satisfy the preconditions on n and w. There is
	// therefore no chance for this to panic.
	if n < 0 {
		panic(errors.New("n must be non-negative"))
	}
	if w < 0 {
		panic(errors.New("w must be non-negative"))
	}
	if n >= 1<<(8*uint(w)) {
		panic(errors.New("integer too large to encode in specified length"))
	}

	out := make([]byte, w)
	for i := w - 1; i >= 0; i-- {
		out[i] = byte(n & 0xFF)
		n >>= 8
	}
	return out
}
