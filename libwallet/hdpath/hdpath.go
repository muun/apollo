package hdpath

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"github.com/btcsuite/btcutil/hdkeychain"
)

type Path string

const HardenedSymbol = "'"

var re = regexp.MustCompile("^(m?|\\/|(([a-z]+:)?\\d+'?))(\\/([a-z]+:)?\\d+'?)*$")

func Parse(s string) (Path, error) {
	if !re.MatchString(s) {
		return "", fmt.Errorf("path is not valid: `%s`", s)
	}
	return Path(s), nil
}

func MustParse(s string) Path {
	p, err := Parse(s)
	if err != nil {
		panic(err)
	}
	return p
}

func (p Path) Child(i uint32) Path {
	isChildHardened := i >= hdkeychain.HardenedKeyStart
	if isChildHardened {
		i -= hdkeychain.HardenedKeyStart
		return Path(fmt.Sprintf("%s/%d'", p, i))
	}
	return Path(fmt.Sprintf("%s/%d", p, i))
}

func (p Path) NamedChild(name string, i uint32) Path {
	isChildHardened := i >= hdkeychain.HardenedKeyStart
	if isChildHardened {
		i -= hdkeychain.HardenedKeyStart
		return Path(fmt.Sprintf("%s/%s:%d'", p, name, i))
	}
	return Path(fmt.Sprintf("%s/%s:%d", p, name, i))
}

func (p Path) String() string {
	return string(p)
}

type PathIndex struct {
	Index    uint32
	Hardened bool
	Name     string
}

// Indexes returns the derivation indexes corresponding to this path.
//
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
func (p Path) Indexes() []PathIndex {
	path := string(p)

	if path == "m" || path == "/" || path == "" {
		return make([]PathIndex, 0)
	}

	var indexes []PathIndex
	path = strings.TrimPrefix(path, "m")
	path = strings.TrimPrefix(path, "/")

	for _, chunk := range strings.Split(path, "/") {
		hardened := false
		indexText := chunk
		if strings.HasSuffix(indexText, HardenedSymbol) {
			hardened = true
			indexText = strings.TrimSuffix(indexText, HardenedSymbol)
		}

		parts := strings.Split(indexText, ":")
		if len(parts) > 2 {
			panic("path is malformed: " + path)
		}

		var name string
		if len(parts) == 2 {
			name = parts[0]
		}

		index, err := strconv.ParseUint(parts[len(parts)-1], 10, 32)
		if err != nil {
			panic("path is malformed: " + err.Error())
		}

		indexes = append(indexes, PathIndex{
			Index:    uint32(index),
			Hardened: hardened,
			Name:     name,
		})
	}

	return indexes
}

// IndexesFrom returns the indexes starting from the given parent path.
func (p Path) IndexesFrom(parentPath Path) []PathIndex {
	return p.Indexes()[len(parentPath.Indexes()):]
}
