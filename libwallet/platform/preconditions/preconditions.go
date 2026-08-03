// Package preconditions contains convenience functions that help a function check whether it was
// invoked correctly (whether its preconditions have been met).
//
// In case the precondition fails, a panic is issued communicating the failure reason.
//
// Preconditions should be used when the condition being checked is assumed to be true, and cases
// where they aren't the system state should be considered unknown. In these situations,
// panicking is the most reasonable action.
//
// Each precondition function has an -f terminated companion that accepts a formatted string that
// will be sent as the panic message upon failure.
// Non -f terminated functions issue a default error message.
package preconditions

import (
	"reflect"

	"github.com/go-errors/errors"
)

// PreconditionError is the error value sent to panic when the precondition fails.
type PreconditionError struct{ error }

type unsigned interface {
	~uint | ~uint8 | ~uint16 | ~uint32 | ~uint64 | ~uintptr
}

type signed interface {
	~int | ~int8 | ~int16 | ~int32 | ~int64 | ~float32 | ~float64
}

type number interface {
	signed | unsigned
}

func CheckState(expr bool) {
	CheckStatef(expr, "state precondition failed")
}

func CheckStatef(expr bool, format string, args ...any) {
	if !expr {
		fail(format, args...)
	}
}

func CheckNotNil[T any](ref T) T {
	return CheckNotNilf(ref, "Expected value to not be nil")
}

func CheckNotNilf[T any](ref T, format string, args ...any) T {
	if interfaceOrUnderlyingValueIsNil(ref) {
		fail(format, args...)
	}
	return ref
}

func CheckNil[T any](ref T) T {
	return CheckNilf(ref, "Expected %v to be null", ref)
}

func CheckNilf[T any](ref T, format string, args ...any) T {
	if !interfaceOrUnderlyingValueIsNil(ref) {
		fail(format, args...)
	}
	return ref
}

// interfaceOrUnderlyingValueIsNil handles the common Go gotcha where the interface has a concrete
// value, so it is not nil, but the underlying value is a pointer type with value nil.
// We check for both things, and fail for both cases.
func interfaceOrUnderlyingValueIsNil(ref any) bool {
	if ref == nil {
		return true
	}

	v := reflect.ValueOf(ref)
	switch v.Kind() {
	case reflect.Chan, reflect.Func, reflect.Map, reflect.Pointer, reflect.UnsafePointer,
		reflect.Slice:
		return v.IsNil()
	default:
		return false
	}
}

func CheckNotEmpty(s string) string {
	return CheckNotEmptyf(s, "Expected string value to be not empty")
}

func CheckNotEmptyf(s string, format string, args ...any) string {
	if s == "" {
		fail(format, args...)
	}
	return s
}

func CheckEmpty(s string) string {
	return CheckEmptyf(s, "Expected string value to be empty")
}

func CheckEmptyf(s string, format string, args ...any) string {
	if s != "" {
		fail(format, args...)
	}
	return s
}

func CheckNotNegative[T signed](n T) T {
	return CheckNotNegativef(n, "Expected %v to be not negative", n)
}

func CheckNotNegativef[T signed](n T, format string, args ...any) T {
	if n < 0 {
		fail(format, args...)
	}
	return n
}

func CheckPositive[T number](n T) T {
	return CheckPositivef(n, "Expected %v to be strictly positive", n)
}

func CheckPositivef[T number](n T, format string, args ...any) T {
	if n <= 0 {
		fail(format, args...)
	}
	return n
}

func fail(format string, args ...any) {
	panic(PreconditionError{errors.Errorf(format, args...)})
}
