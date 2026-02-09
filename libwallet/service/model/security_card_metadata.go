package model

type SecurityCardMetadataJson struct {
	GlobalPublicKeyInHex string `json:"globalPublicKeyInHex"`
	CardVendorInHex      string `json:"cardVendorInHex"`
	CardModelInHex       string `json:"cardModelInHex"`
	FirmwareVersion      uint16 `json:"firmwareVersion"`
	UsageCount           uint16 `json:"usageCount"`
	LanguageCodeInHex    string `json:"languageCodeInHex"`
}
