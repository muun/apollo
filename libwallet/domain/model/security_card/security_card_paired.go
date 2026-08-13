package security_card

type SecurityCardPaired struct {
	Metadata          *SecurityCardMetadata
	IsKnownProvider   bool
	IsCardAlreadyUsed bool
}

func NewSecurityCardPaired(
	metadata *SecurityCardMetadata,
	isKnownProvider bool,
	isCardAlreadyUsed bool,
) *SecurityCardPaired {
	return &SecurityCardPaired{
		Metadata:          metadata,
		IsKnownProvider:   isKnownProvider,
		IsCardAlreadyUsed: isCardAlreadyUsed,
	}
}
