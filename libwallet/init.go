package libwallet

// Listener is an interface implemented by the apps to receive notifications
// of data changes from the libwallet code. Each change is reported with a
// string tag identifying the type of change.
type Listener interface {
	OnDataChanged(tag string)
}

// Config defines the global libwallet configuration.
type Config struct {
	DataDir  string
	Listener Listener
}

var cfg *Config

// Init configures the libwallet
func Init(c *Config) {
	cfg = c
}
