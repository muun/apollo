package app_provided_data

// Config defines the global libwallet configuration.
type Config struct {
	Platform                       string
	OsVersion                      string
	DataDir                        string
	SocketPath                     string
	FeatureStatusProvider          BackendActivatedFeatureStatusProvider
	AppLogSink                     AppLogSink
	HttpClientSessionProvider      HttpClientSessionProvider //nolint:staticcheck // should be HTTPClientSessionProvider, but it's part of the gomobile contract with the apps
	NfcBridge                      NfcBridge
	KeyProvider                    KeyProvider
	SecureKeyValueStore            SecureKeyValueStore
	Network                        string
	HoneycombAPIKey                string
	HoneycombDataset               string
	AndroidBackgroundActionsBridge AndroidBackgroundActionsBridge
	IOSBackgroundActionsBridge     IOSBackgroundActionsBridge
	SessionProvider                SessionProvider
	PeriodicActionsBridge          PeriodicActionsBridge
}

func (c *Config) SetIosBackgroundActionsBridge(
	iosBackgroundActionsBridge IOSBackgroundActionsBridge,
) {
	// There is a bug on gomobile that makes fields starting with an acronym not get initialized
	// due to a mismatch between the generated code from both platforms.
	// Having this setter with this exact casing sidesteps this issue.
	c.IOSBackgroundActionsBridge = iosBackgroundActionsBridge
}
