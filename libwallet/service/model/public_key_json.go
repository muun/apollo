package model

type PublicKeyJson struct { //nolint:staticcheck // TODO: type PublicKeyJson should be PublicKeyJSON
	Key  string `json:"key"`
	Path string `json:"path"`
}
