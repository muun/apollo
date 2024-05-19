package newop

import (
	"strings"

	"github.com/muun/libwallet/operation"
)

const (
	UtxosStatusConfirmed   = operation.UtxosStatusConfirmed
	UtxosStatusUnconfirmed = operation.UtxosStatusUnconfirmed
)

type SizeForAmount struct {
	SizeInVByte int64
	AmountInSat int64
	Outpoint    string
	UtxoStatus  string
}

// NextTransactionSize is a struct used for calculating fees in terms of the
// unspent outputs required to perform a transaction
type NextTransactionSize struct {
	SizeProgression     []SizeForAmount
	ValidAtOperationHid int64 // Just for debugging reasons
	ExpectedDebtInSat   int64
}

func (w *NextTransactionSize) AddSizeForAmount(item *SizeForAmount) {
	w.SizeProgression = append(w.SizeProgression, *item)
}

func (w *NextTransactionSize) GetOutpoints() string {
	var sb strings.Builder
	for i, sizeForAmount := range w.SizeProgression {
		sb.WriteString(sizeForAmount.Outpoint)
		if i != len(w.SizeProgression)-1 { // avoid trailing \n at the end
			sb.WriteRune('\n')
		}

	}
	return sb.String()
}

func (w *NextTransactionSize) toInternalType() *operation.NextTransactionSize {
	var sizeProgression []operation.SizeForAmount
	for _, sizeForAmount := range w.SizeProgression {
		status, found := operation.MapUtxoStatus(sizeForAmount.UtxoStatus)
		if !found {
			panic("unknown utxo status") // TODO(newop): replace panic and bubble up errors?
		}
		sizeProgression = append(sizeProgression, operation.SizeForAmount{
			SizeInVByte: sizeForAmount.SizeInVByte,
			AmountInSat: sizeForAmount.AmountInSat,
			Outpoint:    sizeForAmount.Outpoint,
			UtxoStatus:  status,
		})
	}
	return &operation.NextTransactionSize{
		SizeProgression:   sizeProgression,
		ExpectedDebtInSat: w.ExpectedDebtInSat,
	}
}
