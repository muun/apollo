package app_provided_data

// BackendActivatedFeatureStatusProvider is an interface implemented by the
// apps to provide us with information about the state of some backend side
// feature flags until we can implement a libwallet-side solution for this.
type BackendActivatedFeatureStatusProvider interface {
	IsBackendFlagEnabled(flag string) bool
}
