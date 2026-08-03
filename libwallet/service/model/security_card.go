package model

type SecurityCardJson struct { //nolint:staticcheck // TODO: type SecurityCardJson should be SecurityCardJSON
	Id       string        `json:"id"`       //nolint:staticcheck // TODO: struct field Id should be ID
	AssetUrl string        `json:"assetUrl"` //nolint:staticcheck // TODO: struct field AssetUrl should be AssetURL
	Tag      string        `json:"tag"`
	SpecId   string        `json:"specId"` //nolint:staticcheck // TODO: struct field SpecId should be SpecID
	CardCost PriceInfoJson `json:"cardCost"`
}
