package libwallet

import "os"

func setup() {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		panic(err)
	}

	Init(&Config{
		DataDir: dir,
	})
}
