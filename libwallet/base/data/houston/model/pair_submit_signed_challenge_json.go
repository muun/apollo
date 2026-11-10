package model

// PairSubmitSignedChallengeJSON is the V3 pair submit request.
type PairSubmitSignedChallengeJSON struct {
	CardPubKeyInHex string `json:"cardPubKeyInHex"`
	Index           uint16 `json:"index"`
	MetadataInHex   string `json:"metadataInHex"`
	MacInHex        string `json:"macInHex"`
}
