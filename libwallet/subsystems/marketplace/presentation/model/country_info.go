package model

type CountryInfo struct {
	Code string
}

func NewCountryInfo(code string) CountryInfo {
	return CountryInfo{Code: code}
}
