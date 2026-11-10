package model

// Function names a function the Background Actions subsystem can call. Every value is declared
// here.
type Function string

const (
	PlatformPullNotifications Function = "platform.pull_notifications"
	PlatformSyncRealTimeData  Function = "platform.sync_real_time_data"
	PlatformRegisterInvoices  Function = "platform.register_invoices"
)
