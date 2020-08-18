package libwallet

import "io/ioutil"

func setup() {
	dir, err := ioutil.TempDir("", "libwallet")
	if err != nil {
		panic(err)
	}

	Init(&Config{
		DataDir: dir,
	})
}
