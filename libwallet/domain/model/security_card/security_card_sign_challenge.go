package security_card

// TODO reason should be part of this model (MAC commits to a specific reason)
type SecurityCardSignChallenge struct {
	ServerPublicKey []byte
	Mac             []byte
	CardUsageCount  uint16
	PairingSlot     uint16
}
