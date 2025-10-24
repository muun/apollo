package model

type RegisterSecurityCardOkJson struct {
	Metadata          SecurityCardMetadataJson `json:"metadata"`
	IsKnownProvider   bool                     `json:"isKnownProvider"`
	IsCardAlreadyUsed bool                     `json:"isCardAlreadyUsed"`
}
