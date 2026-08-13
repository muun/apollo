package security_card

import "time"

// pendingPairChallengeTTLSeconds matches Houston's challenge TTL.
const pendingPairChallengeTTLSeconds = 90

// PendingPairChallenge bridges PairRequestChallenge and
// PairSignAndSubmitChallenge: the request RPC persists it, the submit
// RPC reads it back to drive the NFC tap.
type PendingPairChallenge struct {
	ServerPubKeyInHex  string
	ReceivedAtInMillis int64
}

func (c *PendingPairChallenge) IsStale() bool {
	age := time.Since(time.UnixMilli(c.ReceivedAtInMillis)).Seconds()
	return age > pendingPairChallengeTTLSeconds
}
