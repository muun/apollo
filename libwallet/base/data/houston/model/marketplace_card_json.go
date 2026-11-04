package model

type MarketplaceCardJSON struct {
	UUID          string  `json:"uuid"`
	ProviderUUID  string  `json:"providerUuid"`
	SKU           string  `json:"sku"`
	ImageURL      string  `json:"imageUrl"`
	Material      string  `json:"material"`
	WidthMm       float32 `json:"widthMm"`
	HeightMm      float32 `json:"heightMm"`
	ThicknessMm   float32 `json:"thicknessMm"`
	WeightGrams   float32 `json:"weightGrams"`
	SecureElement string  `json:"secureElement"`
	HasStock      bool    `json:"hasStock"`
}
