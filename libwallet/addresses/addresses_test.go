package addresses

import (
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/muun/libwallet/hdpath"
)

func parseKey(s string) *hdkeychain.ExtendedKey {
	key, err := hdkeychain.NewKeyFromString(s)
	if err != nil {
		panic(err)
	}
	return key
}

func derive(key *hdkeychain.ExtendedKey, fromPath, toPath string) *hdkeychain.ExtendedKey {
	indexes := hdpath.MustParse(toPath).IndexesFrom(hdpath.MustParse(fromPath))
	for _, index := range indexes {
		var err error
		var modifier uint32
		if index.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}
		key, err = key.Child(index.Index | modifier)
		if err != nil {
			panic(err)
		}
	}
	return key
}
