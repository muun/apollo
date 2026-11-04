package model

type SecurityCardPaired struct {
	IsKnownProvider   bool
	IsCardAlreadyUsed bool
}

func NewSecurityCardPaired(
	isKnownProvider bool,
	isCardAlreadyUsed bool,
) *SecurityCardPaired {
	return &SecurityCardPaired{
		IsKnownProvider:   isKnownProvider,
		IsCardAlreadyUsed: isCardAlreadyUsed,
	}
}
