package testelectrum

import (
	"bytes"
	"context"
	"encoding/hex"
	"testing"
	"time"

	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	electrum "github.com/muun/libwallet/electrum/v2"
	"github.com/muun/libwallet/platform/test/testbitcoind"
)

const (
	electrumHost = "localhost:60002" // electrum-test-electrs
)

// Client wraps an electrum.Client to provide test-friendly helpers
// for querying a local Electrum server. Methods that encounter errors call
// t.Fatal, so tests do not need explicit error checks.
type Client struct {
	electrum.Client
	*testing.T
}

// NewClient connects to the local Electrum server and registers
// a cleanup that disconnects when the test finishes.
func NewClient(ctx context.Context, t *testing.T) *Client {
	t.Helper()

	client, err := electrum.NewClient(ctx, electrumHost, electrum.RequireTCP)
	if err != nil {
		t.Fatalf("Failed to connect to Electrum at %s: %v", electrumHost, err)
	}

	testClient := &Client{
		Client: client,
		T:      t,
	}
	t.Cleanup(func() { client.Disconnect(ctx) })

	return testClient
}

// StartNewTest rebinds the client to a new sub-test's testing.T and restores
// the previous one on cleanup, allowing a shared client across table tests.
func (e *Client) StartNewTest(t *testing.T) {
	prevT := e.T
	e.T = t
	t.Cleanup(func() { e.T = prevT })
}

// GetBlockHeight returns the current tip height as reported by the Electrum server.
func (e *Client) GetBlockHeight(ctx context.Context) int32 {
	headers, err := e.GetHeaders(ctx)
	if err != nil {
		e.Fatalf("Failed to get electrum headers: %v", err)
	}
	return headers.Height
}

// WaitForIndexedTx polls electrum until the given transaction is indexed.
func (e *Client) WaitForIndexedTx(ctx context.Context, txID string) {
	e.Helper()

	err := e.waitFor(func() bool {
		e.Helper()

		txHex, err := e.GetTransaction(ctx, txID)
		if err != nil || txHex == "" {
			return false
		}

		e.Logf("Electrum indexed transaction %s", txID)
		return true
	})
	if err != nil {
		e.Fatalf("Electrum did not index transaction %s: %v", txID, err)
	}
}

// WaitForConfirmedTx polls electrum until the given transaction appears
// with a positive height (confirmed) in the scripthash history for the given
// script hash.
// Note that this guarantees that electrum not only indexed the relevant tx,
// but also the block containing it.
func (e *Client) WaitForConfirmedTx(ctx context.Context, scriptHash, txID string) {
	e.Helper()

	err := e.waitFor(func() bool {
		e.Helper()

		history, err := e.GetScriptHashHistory(ctx, scriptHash)
		if err != nil {
			return false
		}

		for _, entry := range history {
			if entry.TxHash == txID && entry.Height > 0 {
				e.Logf("Electrum confirmed transaction %s at height %d", txID, entry.Height)
				return true
			}
		}

		return false
	})
	if err != nil {
		e.Fatalf("Electrum did not confirm transaction %s: %v", txID, err)
	}
}

// WaitForBlockHeight polls until the Electrum server reaches at least the given height.
func (e *Client) WaitForBlockHeight(ctx context.Context, blockHeight int32) {
	e.Helper()

	err := e.waitFor(func() bool {
		e.Helper()
		return e.GetBlockHeight(ctx) >= blockHeight
	})
	if err != nil {
		e.Fatalf("Electrum didn't reach block height %d: %v", blockHeight, err)
	}
}

// SyncWithBitcoind waits until the Electrum server has indexed up to bitcoind's current height.
func (e *Client) SyncWithBitcoind(ctx context.Context, bitcoind *testbitcoind.Client) {
	e.WaitForBlockHeight(ctx, bitcoind.GetBlockCount())
}

// AssertTxNotInMempool checks that a transaction with the given hex has NOT been broadcast
// by verifying its txid is not known to electrum.
func (e *Client) AssertTxNotInMempool(ctx context.Context, txHex string) {
	e.Helper()

	if txHex == "" {
		e.Fatalf("txHex is empty")
	}

	rawBytes, err := hex.DecodeString(txHex)
	if err != nil {
		e.Fatalf("failed to decode hex: %v", err)
	}

	var tx wire.MsgTx
	if err = tx.Deserialize(bytes.NewReader(rawBytes)); err != nil {
		e.Fatalf("failed to deserialize tx: %v", err)
	}

	txID := tx.TxHash().String()

	txResult, err := e.GetTransaction(ctx, txID)
	if !errors.As(err, &electrum.ElectrumError{}) || txResult != "" {
		e.Fatalf("Expected transaction %s to NOT be broadcast, but it was found", txID)
	}
}

func (e *Client) waitFor(condition func() bool) error {
	e.Helper()

	const pollInterval = 500 * time.Millisecond
	const maxWait = 30 * time.Second
	deadline := time.Now().Add(maxWait)

	for time.Now().Before(deadline) {
		if condition() {
			return nil
		}
		time.Sleep(pollInterval)
	}

	return errors.Errorf("timeouted after %d seconds", maxWait/time.Second)
}
