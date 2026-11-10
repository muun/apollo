package data

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/require"

	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
)

// The stored control plane outlives the build that wrote it, and a changed
// shape would decode with silently zeroed fields. This golden pins the
// storage shape: if it fails, bump controlPlaneSchemaVersion and update the
// golden here. It lives in-package (unlike the subsystem's other unit
// tests) because the storage shape is deliberately unexported.
func TestControlPlaneStorageShapeIsPinned(t *testing.T) {
	envelope := controlPlaneEnvelope{
		SchemaVersion:      controlPlaneSchemaVersion,
		StoredAtUnixMillis: 1700000000000,
		Payload: mapControlPlaneToJSON(
			internal_model.NewControlPlane(
				[]string{"US"},
				map[string][]string{"provider-1": {"US"}},
				[]string{"card-1"},
			),
		),
	}

	got, err := json.Marshal(envelope)
	require.NoError(t, err)
	require.JSONEq(t, `{
		"schemaVersion": 1,
		"storedAtUnixMillis": 1700000000000,
		"payload": {
			"countries": ["US"],
			"listings": [
				{"providerUuid": "provider-1", "countries": ["US"]}
			],
			"cards": ["card-1"]
		}
	}`, string(got),
		"control plane storage shape changed: "+
			"bump controlPlaneSchemaVersion and update this golden")
}
