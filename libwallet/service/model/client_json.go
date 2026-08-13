package model

type ClientJson struct { //nolint:staticcheck // TODO: type ClientJson should be ClientJSON
	Type        string `json:"type"`
	BuildType   string `json:"buildType"`
	Version     int    `json:"version"`
	VersionName string `json:"versionName"`
	Language    string `json:"language"`
	// TODO: Add rest of attributes for background execution metrics
}
