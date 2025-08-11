package app_provided_data

// Config defines the global libwallet configuration.
type Config struct {
	DataDir                   string
	SocketPath                string
	FeatureStatusProvider     BackendActivatedFeatureStatusProvider
	AppLogSink                AppLogSink
	HttpClientSessionProvider HttpClientSessionProvider
	NfcBridge                 NfcBridge
	KeyProvider               KeyProvider
	Network                   string
}
