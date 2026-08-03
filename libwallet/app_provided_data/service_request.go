package app_provided_data

type Session struct {
	ClientVersion              string
	ClientVersionName          string
	ClientSdkVersion           string
	Language                   string
	ClientType                 string
	AuthToken                  string
	BaseURL                    string
	BackgroundExecutionMetrics string
	DeviceToken                string
}

type HttpClientSessionProvider interface { //nolint:staticcheck // should be HTTPClientSessionProvider, but it's part of the gomobile contract with the apps
	Session() (*Session, error)
	SetSessionStatus(status string)
	SetMinClientVersion(minClientVersion string)
	SetAuthToken(authToken string)
}
