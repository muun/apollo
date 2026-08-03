package model

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/require"
)

// TestNewOpActionMarshalJSONFlatWireFormat pins the flat wire shape the
// V3 client and Houston (Jackson @JsonTypeInfo EXISTING_PROPERTY) must
// agree on: the discriminator sits at the same level as the variant
// fields, not nested.
func TestNewOpActionMarshalJSONFlatWireFormat(t *testing.T) {
	action := NewOpAction{
		DestinationAddress: "bc1qexampleaddress",
		AmountInSats:       12345,
	}

	got, err := json.Marshal(action)
	require.NoError(t, err)

	require.JSONEq(t,
		`{"type":"NEW_OP","destinationAddress":"bc1qexampleaddress","amountInSats":12345}`,
		string(got),
	)
}

// TestNewOpActionMarshalsThroughInterface guards that the type tag
// survives when marshaling via the ActionDescriptor interface (the
// MarshalJSON dispatch is what injects it) and that ActionType matches
// the emitted discriminator.
func TestNewOpActionMarshalsThroughInterface(t *testing.T) {
	var descriptor ActionDescriptor = NewOpAction{
		DestinationAddress: "addr",
		AmountInSats:       1,
	}

	got, err := json.Marshal(descriptor)
	require.NoError(t, err)

	require.JSONEq(t,
		`{"type":"NEW_OP","destinationAddress":"addr","amountInSats":1}`,
		string(got),
	)
	require.Equal(t, ActionTypeNewOp, descriptor.ActionType())
}
