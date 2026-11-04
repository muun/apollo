package app_provided_data

type Platform string

// The values are untyped so that gomobile binds them, as it skips named types.
const (
	Android = "ANDROID"
	IOS     = "IOS"
)

func (p Platform) IOS() bool {
	return p == IOS
}

func (p Platform) Android() bool {
	return p == Android
}
