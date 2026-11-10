package errors

import (
	"github.com/go-errors/errors"
)

type Error struct {
	err  error
	code int64
}

func (e *Error) Error() string {
	return e.err.Error()
}

func (e *Error) Code() int64 {
	return e.code
}

func (e *Error) Unwrap() error {
	return e.err
}

func New(code int64, msg string) error {
	return &Error{errors.New(msg), code}
}

func Errorf(
	code int64,
	format string,
	a ...any,
) error {
	err := errors.Errorf(format, a...)
	return &Error{err, code}
}
