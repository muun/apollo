package libwallet

import (
	"os"
	"path"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/walletdb"
)

func setup() {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		panic(err)
	}

	Init(&app_provided_data.Config{
		DataDir: dir,
	})

	if Pool != nil {
		Pool.Close()
	}
	pool, err := walletdb.NewPool(path.Join(dir, "wallet.db"), nil)
	if err != nil {
		panic(err)
	}
	Pool = pool
}
