// Command checksum prints the SHA-256 of the linter source inputs. Used by
// the toolkit, pre-commit hook, and TestSourceChecksum to detect when the
// custom golangci-lint binary needs rebuilding.
//
// This command must NOT import the linters package. Doing so would compile the
// linters package (and its //go:embed .checksum) every time the toolkit
// computes the checksum, poisoning Go's build cache with whatever .checksum
// value exists at that moment — before the toolkit has a chance to update it.
package main

import (
	"context"
	"crypto/sha256"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
)

func main() {
	repoRoot := gitRoot()
	checksum, err := computeSourceChecksum(repoRoot)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Println(checksum)
}

// computeSourceChecksum mirrors linters.ComputeSourceChecksum exactly — same
// files, same order, same hash. Keep both in sync.
func computeSourceChecksum(repoRoot string) (string, error) {
	lintersDir := filepath.Join(repoRoot, "libwallet", "linters")

	var paths []string
	err := filepath.Walk(
		lintersDir,
		func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if info.IsDir() {
				if info.Name() == "testdata" || info.Name() == "cmd" {
					return filepath.SkipDir
				}
				return nil
			}
			isSrc := strings.HasSuffix(path, ".go") &&
				!strings.HasSuffix(path, "_test.go")
			isMod := info.Name() == "go.mod" || info.Name() == "go.sum"
			if isSrc || isMod {
				paths = append(paths, path)
			}
			return nil
		})
	if err != nil {
		return "", err
	}
	paths = append(paths,
		filepath.Join(repoRoot, ".custom-gcl.yml"),
	)
	sort.Strings(paths)

	h := sha256.New()
	for _, p := range paths {
		data, err := os.ReadFile(p)
		if err != nil {
			return "", err
		}
		h.Write(data)
	}
	return fmt.Sprintf("%x", h.Sum(nil)), nil
}

func gitRoot() string {
	cmd := exec.CommandContext(context.Background(),
		"git", "rev-parse", "--show-toplevel")
	out, err := cmd.Output()
	if err != nil {
		fmt.Fprintln(os.Stderr, "cannot find git root:", err)
		os.Exit(1)
	}
	return strings.TrimSpace(string(out))
}
