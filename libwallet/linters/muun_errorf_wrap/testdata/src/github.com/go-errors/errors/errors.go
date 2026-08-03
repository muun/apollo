package errors

type Error struct{ s string }

func (e *Error) Error() string { return e.s }

func Errorf(format string, a ...interface{}) *Error { return nil }
