package addresses

import (
	"strings"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/base/domain/cryptography/hdpath"
)

// schemaBasePath is the base path every wallet derivation path starts from.
var schemaBasePath = hdpath.MustParse(hd_keys.SchemaBasePath)

const (
	changeBranch   = 0
	externalBranch = 1
)

// validateAddressDerivationPath checks that path is an address path:
//
//	m/schema:1'/recovery:1'/<change:0|external:1>/<index>
//
// with the branch and index unhardened.
func validateAddressDerivationPath(path string) error {
	if !strings.HasPrefix(path, "m/") {
		return errors.Errorf("expected address path %v to be rooted at m", path)
	}

	parsed, err := hdpath.Parse(path)
	if err != nil {
		return err
	}

	if !parsed.HasPrefix(schemaBasePath) {
		return errors.Errorf(
			"expected address path %v to have the schema base path as a prefix", path,
		)
	}

	indexes := parsed.IndexesFrom(schemaBasePath)
	if len(indexes) != 2 {
		return errors.Errorf(
			"expected address path %v to be two levels below the schema base path", path,
		)
	}

	branch, index := indexes[0], indexes[1]
	isAddressBranch := branch.Index == changeBranch || branch.Index == externalBranch
	if branch.Hardened || !isAddressBranch || index.Hardened {
		return errors.Errorf(
			"expected address path %v to be an unhardened child of the change or external branch",
			path,
		)
	}

	return nil
}
