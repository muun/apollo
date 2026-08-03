package errorfwrap

import (
	"other"

	"github.com/go-errors/errors"
)

func badV(err error) {
	_ = errors.Errorf("failed: %v", err) // want `errors\.Errorf call has %v for error argument; use %w to wrap it`
}

func badS(err error) {
	_ = errors.Errorf("failed: %s", err) // want `errors\.Errorf call has %s for error argument; use %w to wrap it`
}

func badMixed(name string, err error) {
	_ = errors.Errorf("user %s: %v", name, err) // want `errors\.Errorf call has %v for error argument; use %w to wrap it`
}

func badConcrete(err *concreteErr) {
	_ = errors.Errorf("failed: %v", err) // want `errors\.Errorf call has %v for error argument; use %w to wrap it`
}

func goodW(err error) {
	_ = errors.Errorf("failed: %w", err)
}

func goodMixed(name string, err error) {
	_ = errors.Errorf("user %s: %w", name, err)
}

func goodNonError(s string) {
	_ = errors.Errorf("value: %v", s)
}

func goodNoArgs() {
	_ = errors.Errorf("static message")
}

func goodSingleNonError(n int) {
	_ = errors.Errorf("count: %d", n)
}

func goodPercent(err error) {
	_ = errors.Errorf("100%% failed: %w", err)
}

func goodOtherPackage(err error) {
	_ = other.Errorf("failed: %v", err)
}

func goodType(err error) {
	_ = errors.Errorf("unexpected error type %T", err)
}

type concreteErr struct{}

func (e *concreteErr) Error() string { return "" }
