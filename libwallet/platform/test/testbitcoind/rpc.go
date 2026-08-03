package testbitcoind

import (
	"encoding/json"
	"slices"

	"github.com/btcsuite/btcd/btcjson"
	"github.com/go-errors/errors"
)

func makeRequest[Result any](b *Client, method string, args ...any) (Result, error) {
	b.Helper()

	// Remove nils
	args = slices.DeleteFunc(args, func(arg any) bool { return arg == nil })

	var result Result

	rawArgs := make([]json.RawMessage, len(args))
	for i, arg := range args {
		data, err := json.Marshal(arg)
		if err != nil {
			return result, errors.Errorf("failed to marshal arg %d: %w", i, err)
		}
		rawArgs[i] = data
	}

	rawResult, err := b.RawRequest(method, rawArgs)
	if err != nil {
		return result, errors.Errorf("method %s failed: %w", method, err)
	}

	if err := json.Unmarshal(rawResult, &result); err != nil {
		return result, errors.Errorf("failed to unmarshal %s response: %w", method, err)
	}

	return result, nil
}

func mustMakeRequest[Result any](b *Client, method string, args ...any) Result {
	b.Helper()

	result, err := makeRequest[Result](b, method, args...)
	if err != nil {
		b.Fatal(err)
	}
	return result
}

func checkResponseCode(err error, expectedCodes ...btcjson.RPCErrorCode) bool {
	var rpcErr *btcjson.RPCError
	if !errors.As(err, &rpcErr) {
		return false
	}

	for _, expectedCode := range expectedCodes { //nolint:modernize // TODO: use slices.Contains
		if rpcErr.Code == expectedCode {
			return true
		}
	}

	return false
}
