package libwallet

import (
	"os"

	"github.com/muun/libwallet/app_provided_data"
)

func setup() {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		panic(err)
	}

	Init(&app_provided_data.Config{
		DataDir: dir,
	})
}
