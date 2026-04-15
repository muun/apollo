package assets

import (
	_ "embed"
)

// Embed image files as binary data in the compiled binary
// This eliminates the need for external image files at runtime

//go:embed images/padlock.png
var PadlockPNG []byte

//go:embed images/help.png
var HelpPNG []byte

const (
	PadlockImageName = "padlock"
	HelpImageName    = "help"
)
