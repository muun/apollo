package test

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/secret"
	"github.com/muun/libwallet/platform/test/testsecret"
)

func TestBytes(t *testing.T) {
	value := []byte{0xde, 0xad, 0xbe, 0xef}
	wrapped := secret.NewBytes(value)
	secretForms := testsecret.PrintedForms(value)

	require.Equal(t, value, wrapped.Reveal())
	require.Equal(t, 4, wrapped.Len())
	require.Equal(t, secret.Placeholder, wrapped.String())
	require.Equal(t, secret.NewBytes([]byte{0xde, 0xad, 0xbe, 0xef}), wrapped)
	testsecret.RequireRedacted(t, wrapped, secretForms...)
	testsecret.RequireRedacted(t, &wrapped, secretForms...)

	holder := struct{ preimage secret.Bytes[[]byte] }{wrapped}
	for _, verb := range []string{"%s", "%q", "%x", "%d"} {
		for _, secretForm := range secretForms {
			require.NotContains(t, fmt.Sprintf(verb, holder), secretForm, verb)
		}
	}
}

func TestBytesCopies(t *testing.T) {
	value := []byte{0xde, 0xad, 0xbe, 0xef}
	wrapped := secret.NewBytes(value)

	value[0] = 0x00
	wrapped.Reveal()[1] = 0x00

	require.Equal(t, []byte{0xde, 0xad, 0xbe, 0xef}, wrapped.Reveal())
}

func TestBytesArray(t *testing.T) {
	var value [32]byte
	for i := range value {
		value[i] = 0xa0 + byte(i)
	}
	wrapped := secret.NewBytes(value)
	secretForms := testsecret.PrintedForms(value[:])

	require.Equal(t, value, wrapped.Reveal())
	require.Equal(t, 32, wrapped.Len())
	testsecret.RequireRedacted(t, wrapped, secretForms...)
	testsecret.RequireRedacted(t, &wrapped, secretForms...)

	holder := struct{ sessionIDs []secret.Bytes[[32]byte] }{[]secret.Bytes[[32]byte]{wrapped}}
	for _, verb := range []string{"%s", "%q", "%x", "%d"} {
		for _, secretForm := range secretForms {
			require.NotContains(t, fmt.Sprintf(verb, holder), secretForm, verb)
		}
	}
}

func TestBytesZeroValue(t *testing.T) {
	var zero secret.Bytes[[]byte]
	var zeroArray secret.Bytes[[32]byte]

	require.Nil(t, zero.Reveal())
	require.Equal(t, 0, zero.Len())
	require.Equal(t, [32]byte{}, zeroArray.Reveal())
	require.Equal(t, 32, zeroArray.Len())
}
