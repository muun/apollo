package errors

import (
	"errors"
	"fmt"
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

func New(code int64, msg string) error {
	return &Error{errors.New(msg), code}
}

func Errorf(code int64, format string, a ...interface{}) error {
	err := fmt.Errorf(format, a...)
	return &Error{err, code}
}
