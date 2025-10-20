package model

type SecurityCardMetadataJson struct {
	GlobalPublicKeyInHex string `json:"globalPublicKeyInHex"`
	CardVendorInHex      string `json:"cardVendorInHex"`
	CardModelInHex       string `json:"cardModelInHex"`
	FirmwareVersion      int    `json:"firmwareVersion"`
	UsageCount           int    `json:"usageCount"`
	LanguageCodeInHex    string `json:"languageCodeInHex"`
}
