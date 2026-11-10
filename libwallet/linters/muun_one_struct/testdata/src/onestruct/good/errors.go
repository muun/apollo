package good

// The per-package errors file may hold several error types.
type FirstError struct{}

func (e *FirstError) Error() string { return "first" }

type SecondError struct{}

func (e *SecondError) Error() string { return "second" }
