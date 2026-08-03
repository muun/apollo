package lightning

import (
	"bytes"
	"encoding/hex"

	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"
)

// Transaction is a wrapper over wire.MsgTx with useful methods for M2U operations
type Transaction struct {
	wire.MsgTx
}

func NewTransaction(rawHex string) (*Transaction, error) {
	rawBytes, err := hex.DecodeString(rawHex)
	if err != nil {
		return nil, errors.Errorf("decode hex: %w", err)
	}

	var tx Transaction
	if err := tx.Deserialize(bytes.NewReader(rawBytes)); err != nil {
		return nil, errors.Errorf("deserialize tx: %w", err)
	}

	return &tx, nil
}

// GetID returns the transaction hash
func (t *Transaction) GetID() string {
	return t.TxID()
}

func (t *Transaction) ToRawHex() string {
	var buf bytes.Buffer
	if err := t.Serialize(&buf); err != nil {
		return ""
	}
	return hex.EncodeToString(buf.Bytes())
}
