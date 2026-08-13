package model

type RegisterSecurityCardJson struct { //nolint:staticcheck // TODO: type RegisterSecurityCardJson should be RegisterSecurityCardJSON
	CardPublicKeyInHex   string                   `json:"cardPublicKeyInHex"`
	ClientPublicKeyInHex string                   `json:"clientPublicKeyInHex"`
	PairingSlot          uint16                   `json:"pairingSlot"`
	Metadata             SecurityCardMetadataJson `json:"metadata"`
	MacInHex             string                   `json:"macInHex"`
	GlobalSignCardInHex  string                   `json:"globalSignCardInHex"`
}
