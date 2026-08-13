package lightning

import (
	model "github.com/muun/libwallet/domain/model/lightning"
	electrum "github.com/muun/libwallet/electrum/v2"
)

const (
	unknownTx = -1
)

// chainStatus is a snapshot of the bitcoin chain state.
// Saves the current chain height and accepted transaction's status.
type chainStatus struct {
	currentHeight int
	txStatus      map[string]txStatus
}

type txStatus struct {
	confirmations int
	// We can have more fields in the future (e.g. fee)
}

// newChainStatus creates an empty chain status for the current chain height.
func newChainStatus(currentHeight int) *chainStatus {
	return &chainStatus{
		currentHeight: currentHeight,
		txStatus:      make(map[string]txStatus),
	}
}

// addScriptHashHistory saves all transactions from a script hash history.
func (s *chainStatus) addScriptHashHistory(history []electrum.ScriptHashHistoryEntry) {
	for _, entry := range history {
		if _, ok := s.txStatus[entry.TxHash]; ok {
			continue
		}

		height := int(entry.Height)
		confirmations := s.currentHeight - int(entry.Height) + 1
		if height <= 0 {
			confirmations = 0
		}
		s.txStatus[entry.TxHash] = txStatus{
			confirmations: confirmations,
		}
	}
}

// isPresent checks if the tx is currently accepted by the chain, meaning the tx is confirmed
// or in the mempool.
func (s *chainStatus) isPresent(tx model.Transaction) bool {
	_, ok := s.txStatus[tx.GetID()]
	return ok
}

// getConfirmations returns tx's current confirmation count.
// Unconfirmed txs return 0. Txs not known return unknownTx.
func (s *chainStatus) getConfirmations(tx model.Transaction) int {
	if status, ok := s.txStatus[tx.GetID()]; ok {
		return status.confirmations
	}
	return unknownTx
}
