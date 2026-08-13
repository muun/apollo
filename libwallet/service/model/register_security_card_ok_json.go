package model

type RegisterSecurityCardOkJson struct { //nolint:staticcheck // TODO: type RegisterSecurityCardOkJson should be RegisterSecurityCardOkJSON
	Metadata          SecurityCardMetadataJson `json:"metadata"`
	IsKnownProvider   bool                     `json:"isKnownProvider"`
	IsCardAlreadyUsed bool                     `json:"isCardAlreadyUsed"`
}
