package v2

import "github.com/go-errors/errors"

// Result represents a single response from an Electrum call, which can contain the expected result
// or an error.
type Result[T any] struct {
	Value T
	Err   error
}

func okResult[T any](v T) Result[T] {
	return Result[T]{Value: v}
}

func errResult[T any](e error) Result[T] {
	return Result[T]{Err: e}
}

func (r Result[T]) Unwrap() (T, error) {
	return r.Value, r.Err
}

func (r Result[T]) IsOk() bool {
	return r.Err == nil
}

func (r Result[T]) IsErr() bool {
	return r.Err != nil
}

// ElectrumError returns the Electrum protocol error returned by the server, if any
func (r Result[T]) ElectrumError() *ElectrumError {
	if r.IsOk() {
		return nil
	}

	var electrumError ElectrumError
	if errors.As(r.Err, &electrumError) {
		return &electrumError
	}

	return nil
}
