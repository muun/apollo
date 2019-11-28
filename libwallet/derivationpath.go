package libwallet

import (
	"strconv"
	"strings"

	"github.com/pkg/errors"
)

type derivationIndex struct {
	i        uint32
	hardened bool
	name     string
}

type derivationPath struct {
	indexes []derivationIndex
}

const (
	hardenedSymbol = "'"
)

// parseDerivationPath parses BIP32 paths
// The following paths are valid:
//
// "" (root key)
// "m" (root key)
// "/" (root key)
// "m/0'" (hardened child #0 of the root key)
// "/0'" (hardened child #0 of the root key)
// "0'" (hardened child #0 of the root key)
// "m/44'/1'/2'" (BIP44 testnet account #2)
// "/44'/1'/2'" (BIP44 testnet account #2)
// "44'/1'/2'" (BIP44 testnet account #2)
// "m/schema:1'" (Muun schema path)
//
// The following paths are invalid:
//
// "m / 0 / 1" (contains spaces)
// "m/b/c" (alphabetical characters instead of numerical indexes)
// "m/1.2^3" (contains illegal characters)
func parseDerivationPath(path string) (*derivationPath, error) {
	if path == "m" || path == "/" || path == "" {
		return &derivationPath{indexes: make([]derivationIndex, 0)}, nil
	}

	var indexes []derivationIndex
	path = strings.TrimPrefix(path, "m")
	path = strings.TrimPrefix(path, "/")

	for _, chunk := range strings.Split(path, "/") {
		hardened := false
		indexText := chunk
		if strings.HasSuffix(indexText, hardenedSymbol) {
			hardened = true
			indexText = strings.TrimSuffix(indexText, hardenedSymbol)
		}

		parts := strings.Split(indexText, ":")
		if len(parts) > 2 {
			return nil, errors.New("path is malformed: " + path)
		}

		var name string
		if len(parts) == 2 {
			name = parts[0]
		}

		index, err := strconv.ParseUint(parts[len(parts)-1], 10, 32)
		if err != nil {
			return nil, errors.New("path is malformed " + err.Error())
		}

		indexes = append(indexes, derivationIndex{i: uint32(index), hardened: hardened, name: name})
	}

	return &derivationPath{indexes: indexes}, nil
}
