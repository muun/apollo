package app_provided_data

// SessionUser is the user logged into the app.
type SessionUser struct {
	// HoustonID is the user's id in houston.
	HoustonID int64
}

// SessionUserResponse holds the logged-in user. User is nil when there is none.
// GetUser can't return a nil *SessionUser directly: Swift imports (*SessionUser, error) as a
// non-optional `throws -> SessionUser`, so Falcon couldn't return "no user".
type SessionUserResponse struct {
	User *SessionUser
}

// SessionProvider tells libwallet about the app's session.
type SessionProvider interface {
	// IsLoggedIn returns whether the app has a wallet.
	IsLoggedIn() (bool, error)

	// GetUser returns the logged-in user.
	GetUser() (*SessionUserResponse, error)
}
