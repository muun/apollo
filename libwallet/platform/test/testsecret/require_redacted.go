// Package testsecret checks that values holding secrets never print them.
package testsecret

import (
	"bytes"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"log/slog"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

// RequireRedacted fails the test if fmt prints any of secretForms for value, at top level, in
// an exported struct field or in an unexported one, or if slog's JSON or Text handler logs one
// for value on its own.
// value must be passed in the form the code holds it, usually a pointer.
// In an unexported field only %v, %+v and %#v are checked: fmt can't call methods
// there, and dereferences pointers for verbs like %s.
func RequireRedacted(t *testing.T, value any, secretForms ...string) {
	t.Helper()
	require.NotEmpty(t, secretForms)

	type exportedField struct{ Value any }
	type unexportedField struct{ value any }

	var outputs []string
	for _, verb := range []string{"%v", "%+v", "%#v", "%s", "%q", "%x"} {
		outputs = append(outputs,
			fmt.Sprintf(verb, value),
			fmt.Sprintf(verb, exportedField{value}),
		)
	}
	for _, verb := range []string{"%v", "%+v", "%#v"} {
		outputs = append(outputs, fmt.Sprintf(verb, unexportedField{value}))
	}

	var logs bytes.Buffer
	slog.New(slog.NewJSONHandler(&logs, nil)).Info("Redaction probe", "value", value)
	slog.New(slog.NewTextHandler(&logs, nil)).Info("Redaction probe", "value", value)
	outputs = append(outputs, logs.String())

	for _, output := range outputs {
		for _, secretForm := range secretForms {
			require.NotContains(t, output, secretForm)
		}
	}
}

// PrintedForms returns the forms fmt, slog and the standard encoders print secret in: the
// raw bytes, hex, standard base64 and fmt's decimal byte list.
func PrintedForms(secret []byte) []string {
	return []string{
		string(secret),
		hex.EncodeToString(secret),
		base64.StdEncoding.EncodeToString(secret),
		strings.Trim(fmt.Sprintf("%d", secret), "[]"),
	}
}
