package v2

import (
	"fmt"
)

// ElectrumError is a parsed JSON-RPC 2.0 error representing an electrum error.
// Most (but not necessary all) electrum implementations return an error with this structure.
// Errors that don't conform to this structure are given the Code 0, and the serialized error
// is stored in Message.
type ElectrumError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data"`
}

func (e ElectrumError) Error() string {
	return fmt.Sprintf("%d: %s", e.Code, e.Message)
}

func newUnstructuredElectrumError(err string) error {
	return ElectrumError{
		Code:    0,
		Message: err,
		Data:    nil,
	}
}
