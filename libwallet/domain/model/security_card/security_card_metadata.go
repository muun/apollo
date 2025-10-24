package security_card

type SecurityCardMetadata struct {
	GlobalPublicKeyInHex string
	CardVendorInHex      string
	CardModelInHex       string
	FirmwareVersion      int
	UsageCount           int
	LanguageCodeInHex    string
}
