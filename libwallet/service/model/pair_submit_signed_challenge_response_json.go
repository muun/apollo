package model

// PairSubmitSignedChallengeResponseJSON is the V3 pair submit response. Houston
// has already verified the MAC against the raw metadata bytes from the
// request, so the parsed metadata returned here is trustworthy for the
// client to store and display.
type PairSubmitSignedChallengeResponseJSON struct {
	SecurityCard      PairedSecurityCardJSON `json:"securityCard"`
	IsKnownProvider   bool                   `json:"isKnownProvider"`
	IsCardAlreadyUsed bool                   `json:"isCardAlreadyUsed"`
}

type PairedSecurityCardJSON struct {
	ID       uint64                     `json:"id"`
	Metadata SecurityCardV3MetadataJSON `json:"metadata"`
	PairedAt string                     `json:"pairedAt"`
}
