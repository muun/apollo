package app_provided_data

// Config defines the global libwallet configuration.
type Config struct {
	DataDir                   string
	SocketPath                string
	FeatureStatusProvider     BackendActivatedFeatureStatusProvider
	AppLogSink                AppLogSink
	HttpClientSessionProvider HttpClientSessionProvider //nolint:staticcheck // should be HTTPClientSessionProvider, but it's part of the gomobile contract with the apps
	NfcBridge                 NfcBridge
	KeyProvider               KeyProvider
	SecureKeyValueStorage     SecureKeyValueStorage
	Network                   string
}
