package lightning

import (
	"fmt"
)

// IncomingHTLCRecall represents the recall transaction that the server may
// broadcast and its associated second-stage (recall-incoming-success) transaction.
type IncomingHTLCRecall struct {
	Recall                Transaction
	RecallIncomingSuccess Transaction
}

func NewIncomingHTLCRecall(
	recallTx, recallIncomingSuccess Transaction,
) *IncomingHTLCRecall {
	if len(recallIncomingSuccess.TxIn) == 0 {
		panic("recall-incoming-success tx has no inputs")
	}

	spentIndex := recallIncomingSuccess.TxIn[0].PreviousOutPoint.Index
	if int(spentIndex) >= len(recallTx.TxOut) {
		panic(fmt.Sprintf(
			"recall tx has %d outputs but its second stage spends index %d",
			len(recallTx.TxOut), spentIndex,
		))
	}

	return &IncomingHTLCRecall{
		Recall:                recallTx,
		RecallIncomingSuccess: recallIncomingSuccess,
	}
}

// RecallIncomingSuccessOutputScript returns the output script from the recall
// transaction that the recall-incoming-success transaction spends.
func (h *IncomingHTLCRecall) RecallIncomingSuccessOutputScript() []byte {
	spentIndex := h.RecallIncomingSuccess.TxIn[0].PreviousOutPoint.Index
	return h.Recall.TxOut[spentIndex].PkScript
}
