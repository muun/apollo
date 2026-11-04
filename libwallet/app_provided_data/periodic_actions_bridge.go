package app_provided_data

// PeriodicActionsBridge binds the app's actions that the Background Actions subsystem calls
// periodically. Each call blocks until the action ends, and returns an error when it fails or does
// not end within timeoutMillis.
type PeriodicActionsBridge interface {
	// PullNotifications fetches the pending notifications from Houston and processes them.
	PullNotifications(timeoutMillis int64) error

	// SyncRealTimeData fetches the real-time data from Houston, unless it is fresh.
	SyncRealTimeData(timeoutMillis int64) error

	// RegisterInvoices tops up the invoices registered in Houston.
	RegisterInvoices(timeoutMillis int64) error
}
