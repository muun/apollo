package service

import "github.com/muun/libwallet/app_provided_data"

type TestProvider struct {
	ClientVersion     string
	ClientVersionName string
	Language          string
	ClientType        string
	AuthToken         string
	BaseURL           string
}

// Interface check
var _ app_provided_data.HttpClientSessionProvider = (*TestProvider)(nil)

func (p *TestProvider) Session() (*app_provided_data.Session, error) {
	return &app_provided_data.Session{
		ClientVersion:     p.ClientVersion,
		ClientVersionName: p.ClientVersionName,
		Language:          p.Language,
		ClientType:        p.ClientType,
		AuthToken:         p.AuthToken,
		BaseURL:           p.BaseURL,
	}, nil
}

func (p *TestProvider) SetSessionStatus(string) {}

func (p *TestProvider) SetMinClientVersion(string) {}

func (p *TestProvider) SetAuthToken(authToken string) {
	p.AuthToken = authToken
}
