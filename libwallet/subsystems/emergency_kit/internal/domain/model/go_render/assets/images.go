package assets

import (
	_ "embed"
)

// The icons are quality-100 JPEGs flattened over the exact panel background colors they sit on
// (padlock: RGB(223, 236, 251); help: RGB(246, 249, 255)) because JPEG has no alpha channel.
// gofpdf embeds JPEG verbatim (DCTDecode), so registering them costs no decoding or
// recompression at render time — profiling showed the old PNGs' per-render decode+recompress
// was the single largest cost of the whole kit generation.

//go:embed images/padlock.jpg
var PadlockJPEG []byte

//go:embed images/help.jpg
var HelpJPEG []byte

const (
	PadlockImageName = "padlock"
	HelpImageName    = "help"
)
