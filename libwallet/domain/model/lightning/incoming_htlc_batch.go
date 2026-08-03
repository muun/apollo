package lightning

import (
	"github.com/google/uuid"
)

// IncomingHTLCBatch holds the state of an incoming HTLC batch tracked by the M2U.
type IncomingHTLCBatch struct {
	UUID              string
	Recalls           []*IncomingHTLCRecall
	Enforcements      []*IncomingHTLCEnforcement
	PreimagesRevealed bool
}

func NewIncomingHTLCBatch(
	recalls []*IncomingHTLCRecall,
	enforcements []*IncomingHTLCEnforcement,
) *IncomingHTLCBatch {
	if len(recalls) == 0 {
		panic("expected at least one recall")
	}
	if len(enforcements) == 0 {
		panic("expected at least one enforcement")
	}

	return &IncomingHTLCBatch{
		UUID:         uuid.New().String(),
		Recalls:      recalls,
		Enforcements: enforcements,
	}
}

// GetAllOutputScripts gets all distinct output scripts from the transactions of the batch.
func (b *IncomingHTLCBatch) GetAllOutputScripts() [][]byte {
	seenScripts := make(map[string]bool)
	var outputScripts [][]byte

	for _, recall := range b.Recalls {
		script := recall.RecallIncomingSuccessOutputScript()
		scriptString := string(script)

		if !seenScripts[scriptString] {
			seenScripts[scriptString] = true
			outputScripts = append(outputScripts, script)
		}
	}

	for _, enforcement := range b.Enforcements {
		script := enforcement.EnforcementIncomingSuccessOutputScript()
		scriptString := string(script)

		if !seenScripts[scriptString] {
			seenScripts[scriptString] = true
			outputScripts = append(outputScripts, script)
		}
	}

	return outputScripts
}
