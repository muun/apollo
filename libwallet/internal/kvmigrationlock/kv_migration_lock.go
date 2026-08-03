package kvmigrationlock

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"go/ast"
	"go/format"
	"go/parser"
	"go/token"
	"sort"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/storage"
)

// Lockfile represents the structure of the lockfile on disk.
type Lockfile struct {
	Version    int             `json:"version"`
	Migrations []MigrationLock `json:"migrations"`
}

// MigrationLock holds the hash of a single migration and the individual hashes of its changes.
type MigrationLock struct {
	Description  string   `json:"description"`
	Hash         string   `json:"hash"`
	ChangeHashes []string `json:"change_hashes"`
}

// Generate produces a Lockfile from a migration plan by hashing each change.
// migrationsFilePath must point to the Go source file that defines the plan,
// so that CustomChange function literals can be located and hashed via AST.
func Generate(plan []storage.Migration, migrationsFilePath string) (*Lockfile, error) {
	fset := token.NewFileSet()
	// The 0 mode flag intentionally excludes comments from the AST, so that changing a comment
	// inside an AddCustomChange literal does not affect its hash and invalidate the lockfile.
	fileNode, err := parser.ParseFile(fset, migrationsFilePath, nil, 0)
	if err != nil {
		return nil, errors.Errorf("could not parse %s: %w", migrationsFilePath, err)
	}

	lockfile := &Lockfile{
		Version:    1,
		Migrations: make([]MigrationLock, 0, len(plan)),
	}

	for _, migration := range plan {
		lock := MigrationLock{Description: migration.Description}

		for _, change := range migration.Changes {
			h, err := hashChange(change, fset, fileNode)
			if err != nil {
				return nil, errors.Errorf("migration '%s': %w", migration.Description, err)
			}
			lock.ChangeHashes = append(lock.ChangeHashes, h)
		}

		// Hash the migration as description + change hashes in order.
		// Order is intentionally preserved: swapping two changes within a
		// migration must be detected.
		hw := sha256.New()
		hw.Write([]byte(migration.Description))
		for _, ch := range lock.ChangeHashes {
			hw.Write([]byte(ch))
		}
		lock.Hash = fmt.Sprintf("sha256:%x", hw.Sum(nil))

		lockfile.Migrations = append(lockfile.Migrations, lock)
	}

	return lockfile, nil
}

func hashChange(change storage.Change, fset *token.FileSet, fileNode *ast.File) (string, error) {
	content, err := stableChangeString(change, fset, fileNode)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("sha256:%x", sha256.Sum256([]byte(content))), nil
}

// stableChangeString produces a deterministic string representation of a Change for hashing.
// json.Marshal is not used because ValueType is an interface and would serialize to {} for all
// types.
func stableChangeString(
	change storage.Change,
	fset *token.FileSet,
	fileNode *ast.File,
) (string, error) {
	switch c := change.(type) {
	case storage.KeyDefinition:
		return fmt.Sprintf(
			"KeyDefinition{Key:%s, BackupType:%d, "+
				"BackupSecurity:%d, SecurityCritical:%v, "+
				"ValueType:%T}",
			c.Key, c.BackupType, c.BackupSecurity,
			c.SecurityCritical, c.ValueType,
		), nil

	case storage.TypeMigration:
		return fmt.Sprintf("TypeMigration{Key:%s, NewType:%T}",
			c.Key, c.NewType), nil

	case storage.MappedTypeMigration:
		return fmt.Sprintf("MappedTypeMigration{Key:%s, NewType:%T, Map:%s}",
			c.Key, c.NewType, stableMapString(c.OldToNewMap)), nil

	case storage.MapUpdate:
		return fmt.Sprintf("MapUpdate{Key:%s, Map:%s}",
			c.Key, stableMapString(c.OldToNewMap)), nil

	case storage.CustomChange:
		src, err := findCustomChangeSource(c.ID, fset, fileNode)
		if err != nil {
			return "", err
		}
		return fmt.Sprintf("CustomChange{ID:%s, Step:%s}", c.ID, src), nil

	default:
		return "", errors.Errorf("unknown change type %T", change)
	}
}

// findCustomChangeSource finds and formats the function literal passed to AddCustomChange("id",
// ...) by matching the ID string argument in the AST.
func findCustomChangeSource(id string, fset *token.FileSet, fileNode *ast.File) (string, error) {
	var source string
	var found bool
	var findErr error

	ast.Inspect(fileNode, func(n ast.Node) bool {
		if found || findErr != nil {
			return false
		}
		call, ok := n.(*ast.CallExpr)
		if !ok {
			return true
		}
		ident, ok := call.Fun.(*ast.Ident)
		if !ok || ident.Name != "AddCustomChange" {
			return true
		}
		if len(call.Args) != 2 {
			return true
		}
		lit, ok := call.Args[0].(*ast.BasicLit)
		if !ok || lit.Value != fmt.Sprintf("%q", id) {
			return true
		}
		funcLit, ok := call.Args[1].(*ast.FuncLit)
		if !ok {
			findErr = errors.Errorf(
				"second argument of AddCustomChange(%q, ...) is not a function literal",
				id,
			)
			return false
		}
		var buf bytes.Buffer

		// format.Node produces the canonical representation of the code,
		// normalizing whitespace and empty lines so they don't affect the hash.
		if err := format.Node(&buf, fset, funcLit); err != nil {
			findErr = errors.Errorf("failed to format function literal for id %q: %w", id, err)
			return false
		}
		source = buf.String()
		found = true
		return false
	})

	if findErr != nil {
		return "", findErr
	}
	if !found {
		return "", errors.Errorf("AddCustomChange(%q, ...) not found in migrations file", id)
	}
	return source, nil
}

// stableMapString produces a deterministic string for a map by sorting keys first.
func stableMapString(m map[string]string) string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var buf bytes.Buffer
	buf.WriteString("{")
	for i, k := range keys {
		if i > 0 {
			buf.WriteString(", ")
		}
		fmt.Fprintf(&buf, "%s:%s", k, m[k])
	}
	buf.WriteString("}")
	return buf.String()
}
