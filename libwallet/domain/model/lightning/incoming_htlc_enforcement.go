package lightning

import (
	"fmt"
)

// IncomingHTLCEnforcement represents the enforcement version the client may
// broadcast, alongside its second-stage transaction.
type IncomingHTLCEnforcement struct {
	Enforcement                Transaction
	EnforcementIncomingSuccess Transaction
}

func NewIncomingHTLCEnforcement(
	enforcementTx, enforcementIncomingSuccess Transaction,
) *IncomingHTLCEnforcement {
	if len(enforcementIncomingSuccess.TxIn) == 0 {
		panic("enforcement-incoming-success tx has no inputs")
	}

	spentIndex := enforcementIncomingSuccess.TxIn[0].PreviousOutPoint.Index
	if int(spentIndex) >= len(enforcementTx.TxOut) {
		panic(fmt.Sprintf(
			"enforcement tx has %d outputs but its second stage spends index %d",
			len(enforcementTx.TxOut), spentIndex,
		))
	}

	return &IncomingHTLCEnforcement{
		Enforcement:                enforcementTx,
		EnforcementIncomingSuccess: enforcementIncomingSuccess,
	}
}

// EnforcementIncomingSuccessOutputScript returns the output script from the
// enforcement transaction that the enforcement-incoming-success transaction spends.
func (e *IncomingHTLCEnforcement) EnforcementIncomingSuccessOutputScript() []byte {
	spentIndex := e.EnforcementIncomingSuccess.TxIn[0].PreviousOutPoint.Index
	return e.Enforcement.TxOut[spentIndex].PkScript
}
