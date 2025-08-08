package model

type ClientJson struct {
	Type        string `json:"type"`
	BuildType   string `json:"buildType"`
	Version     int    `json:"version"`
	VersionName string `json:"versionName"`
	Language    string `json:"language"`
	// TODO: Add rest of attributes for background execution metrics
}
