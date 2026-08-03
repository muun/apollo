package security_card

// FreshPairChallenge is a usable pair challenge: it was found in local
// storage and has not yet expired.
type FreshPairChallenge struct {
	ServerPublicKey []byte
}

func NewFreshPairChallenge(serverPublicKey []byte) *FreshPairChallenge {
	return &FreshPairChallenge{ServerPublicKey: serverPublicKey}
}
