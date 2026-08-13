package security_card

type SecurityCardMetadata struct {
	GlobalPublicKeyInHex string
	CardVendorInHex      string
	CardModelInHex       string
	FirmwareVersion      uint16
	UsageCount           uint16
	LanguageCodeInHex    string
}

func NewSecurityCardMetadata(
	globalPublicKeyInHex string,
	cardVendorInHex string,
	cardModelInHex string,
	firmwareVersion uint16,
	usageCount uint16,
	languageCodeInHex string,
) *SecurityCardMetadata {
	return &SecurityCardMetadata{
		GlobalPublicKeyInHex: globalPublicKeyInHex,
		CardVendorInHex:      cardVendorInHex,
		CardModelInHex:       cardModelInHex,
		FirmwareVersion:      firmwareVersion,
		UsageCount:           usageCount,
		LanguageCodeInHex:    languageCodeInHex,
	}
}
