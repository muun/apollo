package security_card

// TODO reason should be part of this model (MAC commits to a specific reason)
type SecurityCardSignChallenge struct {
	ServerPublicKey []byte
	Mac             []byte
	CardUsageCount  uint16
	PairingSlot     uint16
}

func NewSecurityCardSignChallenge(
	serverPublicKey []byte,
	mac []byte,
	cardUsageCount uint16,
	pairingSlot uint16,
) *SecurityCardSignChallenge {
	return &SecurityCardSignChallenge{
		ServerPublicKey: serverPublicKey,
		Mac:             mac,
		CardUsageCount:  cardUsageCount,
		PairingSlot:     pairingSlot,
	}
}
