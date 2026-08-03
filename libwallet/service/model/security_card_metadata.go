package model

type SecurityCardMetadataJson struct { //nolint:staticcheck // TODO: type SecurityCardMetadataJson should be SecurityCardMetadataJSON
	GlobalPublicKeyInHex string `json:"globalPublicKeyInHex"`
	CardVendorInHex      string `json:"cardVendorInHex"`
	CardModelInHex       string `json:"cardModelInHex"`
	FirmwareVersion      uint16 `json:"firmwareVersion"`
	UsageCount           uint16 `json:"usageCount"`
	LanguageCodeInHex    string `json:"languageCodeInHex"`
}
