package security_card

type SecurityCardPaired struct {
	Metadata          *SecurityCardMetadata
	IsKnownProvider   bool
	IsCardAlreadyUsed bool
}
