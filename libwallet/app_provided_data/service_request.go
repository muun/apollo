package app_provided_data

import (
	"fmt"
	"io"
	"log/slog"
)

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

// String returns the session's client fields, without its tokens.
func (s Session) String() string {
	return s.LogValue().String()
}

// Format prints String for every verb.
func (s Session) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, s.String())
}

// LogValue returns the session's client fields, without its tokens.
func (s Session) LogValue() slog.Value {
	return slog.GroupValue(
		slog.String("client_type", s.ClientType),
		slog.String("client_version", s.ClientVersion),
		slog.String("client_version_name", s.ClientVersionName),
		slog.String("client_sdk_version", s.ClientSdkVersion),
		slog.String("language", s.Language),
		slog.String("base_url", s.BaseURL),
	)
}

type HttpClientSessionProvider interface { //nolint:staticcheck // should be HTTPClientSessionProvider, but it's part of the gomobile contract with the apps
	Session() (*Session, error)
	SetSessionStatus(status string)
	SetMinClientVersion(minClientVersion string)
	SetAuthToken(authToken string)
}
