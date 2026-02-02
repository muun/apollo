package security_card

type SecurityCardMetadata struct {
	GlobalPublicKeyInHex string
	CardVendorInHex      string
	CardModelInHex       string
	FirmwareVersion      uint16
	UsageCount           uint16
	LanguageCodeInHex    string
}
