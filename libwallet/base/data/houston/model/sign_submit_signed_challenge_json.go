package model

// SignSubmitSignedChallengeJSON is the V3 sign-submit request body.
type SignSubmitSignedChallengeJSON struct {
	// ShortDeviceIDInHex names the card that answered: the id of the payload
	// the client picked, so Houston knows which card's challenge and secret
	// to check the response against. Empty for a firmware that reports none.
	//
	// NOTE: the spec names the card by its device id
	// (device_model_id || key_agreement_root[-16:]). The mock uses the short
	// device id because it already stores it; the production Houston should
	// use the device id.
	ShortDeviceIDInHex string `json:"shortDeviceIdInHex"`

	CardPubKeyInHex string `json:"cardPubKeyInHex"`
	MacInHex        string `json:"macInHex"`

	// CommitSigInHex is the card's signature over the approved action. The
	// MAC only convinces whoever shares the card's secret; this is what
	// keeps the server from producing a record of an approval the card
	// never gave.
	CommitSigInHex string `json:"commitSigInHex"`

	// ClientSigInHex is the client's BIP340 signature over the same approval,
	// 64 bytes, made with the key at hd_keys.SecurityCardClientApprovalKeyPath.
	// It binds the solved challenge to the wallet, so a response relayed by
	// another wallet's client does not solve it.
	ClientSigInHex string `json:"clientSigInHex"`
}
