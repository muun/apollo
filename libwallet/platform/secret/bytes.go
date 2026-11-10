package secret

import (
	"bytes"
	"fmt"
	"io"
	"log/slog"
)

type byteString interface {
	[]byte | [32]byte
}

// Bytes holds secret bytes and prints Placeholder through String, fmt and slog.
// The bytes sit behind two pointers, which fmt prints as an address with every verb
// even when Bytes sits in an unexported field. reflect.DeepEqual still compares them.
type Bytes[T byteString] struct {
	value **T
}

// NewBytes wraps a copy of value, so later writes to value don't reach the secret.
func NewBytes[T byteString](value T) Bytes[T] {
	inner := clone(value)
	innerPtr := &inner
	return Bytes[T]{value: &innerPtr}
}

// Reveal returns a copy of the secret bytes, the zero T for the zero Bytes.
func (b Bytes[T]) Reveal() T {
	if b.value == nil {
		var zero T
		return zero
	}
	return clone(**b.value)
}

// Len returns the length of the secret bytes.
func (b Bytes[T]) Len() int {
	if b.value == nil {
		var zero T
		return len(zero)
	}
	return len(**b.value)
}

// String returns Placeholder.
func (b Bytes[T]) String() string {
	return Placeholder
}

// Format prints Placeholder for every verb.
func (b Bytes[T]) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, Placeholder)
}

// LogValue returns Placeholder.
func (b Bytes[T]) LogValue() slog.Value {
	return slog.StringValue(Placeholder)
}

// clone copies a []byte. Go already copies a [32]byte by value.
func clone[T byteString](value T) T {
	if s, ok := any(value).([]byte); ok {
		return any(bytes.Clone(s)).(T)
	}
	return value
}
