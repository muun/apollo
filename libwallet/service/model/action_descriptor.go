package model

import "encoding/json"

// ActionType identifies a variant of ActionDescriptor.
type ActionType string

const (
	ActionTypeNewOp ActionType = "NEW_OP"
)

// ActionDescriptor is the discriminated union of sensitive actions a
// security card can approve. Wire format is flat:
//
//	{"type": "<TYPE>", ...variant fields at the same JSON level}
//
// Each variant is a concrete Go struct that returns its own ActionType
// and injects the type tag via MarshalJSON. Embedding json.Marshaler
// makes the marshal contract part of the interface — a variant that
// forgets it won't compile.
//
// Adding a new variant requires:
//  1. Define a new struct.
//  2. Implement ActionType() and MarshalJSON() (compile-enforced by the
//     interface + the `var _ ActionDescriptor = X{}` check below).
type ActionDescriptor interface {
	json.Marshaler
	ActionType() ActionType
}

// Compile-time interface checks. Every concrete variant of
// ActionDescriptor must appear here so the compiler catches missing
// methods (ActionType, MarshalJSON) at build time, not at runtime.
var (
	_ ActionDescriptor = NewOpAction{}
)

// NewOpAction is the descriptor for approving a new on-chain operation.
type NewOpAction struct {
	DestinationAddress string `json:"destinationAddress"`
	AmountInSats       int64  `json:"amountInSats"`
}

// ActionType implements ActionDescriptor.
func (NewOpAction) ActionType() ActionType { return ActionTypeNewOp }

// MarshalJSON injects the discriminator alongside the variant fields,
// matching the {"type": "NEW_OP", ...fields} flat wire format Houston
// emits via Jackson polymorphism (@JsonTypeInfo As.EXISTING_PROPERTY).
//
// The local "alias" type strips NewOpAction's MarshalJSON method so the
// embedded marshaling falls back to default field-by-field encoding
// (otherwise we'd recurse forever).
func (a NewOpAction) MarshalJSON() ([]byte, error) {
	type alias NewOpAction
	return json.Marshal(struct {
		Type ActionType `json:"type"`
		alias
	}{
		Type:  ActionTypeNewOp,
		alias: alias(a),
	})
}
