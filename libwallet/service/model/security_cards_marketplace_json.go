package model

type SecurityCardsMarketplaceJson struct { //nolint:staticcheck // TODO: type SecurityCardsMarketplaceJson should be SecurityCardsMarketplaceJSON
	Providers []SecurityCardsProviderJson `json:"providers"`
	Specs     []SecurityCardSpecJson      `json:"specs"`
}

type SecurityCardSpecJson struct { //nolint:staticcheck // TODO: type SecurityCardSpecJson should be SecurityCardSpecJSON
	SpecId string                                `json:"specId"` //nolint:staticcheck // TODO: struct field SpecId should be SpecID
	Items  map[string][]SecurityCardSpecItemJson `json:"items"`
}

type SecurityCardSpecItemJson struct { //nolint:staticcheck // TODO: type SecurityCardSpecItemJson should be SecurityCardSpecItemJSON
	IconUrl        string `json:"iconUrl"` //nolint:staticcheck // TODO: struct field IconUrl should be IconURL
	Label          string `json:"label"`
	Value          string `json:"value"`
	AdditionalData string `json:"additionalData"`
}
