package model

import "fmt"

// ProviderError is a provider's refusal or failure to answer a request,
// classified by Kind. Failures the user can't act on differently, like a
// malformed request, are plain errors instead.
type ProviderError struct {
	Kind  ProviderErrorKind
	Cause error
}

func NewProviderError(kind ProviderErrorKind, cause error) *ProviderError {
	return &ProviderError{
		Kind:  kind,
		Cause: cause,
	}
}

func (e *ProviderError) Error() string {
	return fmt.Sprintf("%s: %v", e.Kind, e.Cause)
}

func (e *ProviderError) Unwrap() error {
	return e.Cause
}
