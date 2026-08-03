package linters

import (
	"context"
	"crypto/sha256"
	_ "embed"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync"

	"golang.org/x/tools/go/analysis"
)

//go:embed muun_model_constructor/.checksum
var checksum string

var (
	stalenessOnce  sync.Once
	stalenessError string
	reportedStale  bool
	reportedMu     sync.Mutex
)

// ReportStalenessOnce emits the staleness diagnostic at most once across all
// custom analyzers, so a stale binary produces a single message instead of
// one per analyzer.
func ReportStalenessOnce(pass *analysis.Pass) {
	stalenessOnce.Do(func() {
		stalenessError = VerifyStaleness()
	})
	if stalenessError == "" || len(pass.Files) == 0 {
		return
	}
	reportedMu.Lock()
	shouldReport := !reportedStale
	reportedStale = true
	reportedMu.Unlock()
	if shouldReport {
		pass.Reportf(pass.Files[0].Package, "%s", stalenessError)
	}
}

// VerifyStaleness checks whether the embedded checksum matches the current
// linter sources. Returns an error message if stale, empty string if current
// or if the check cannot run (e.g. not inside a git repo).
func VerifyStaleness() string {
	repoRoot, err := findRepoRoot()
	if err != nil {
		log.Printf("muun_model_constructor: skipping staleness check: %v", err)
		return ""
	}
	current, err := ComputeSourceChecksum(repoRoot)
	if err != nil {
		log.Printf("muun_model_constructor: skipping staleness check: %v", err)
		return ""
	}
	if current != strings.TrimSpace(checksum) {
		return "muun-golangci-lint binary is stale — " +
			"run: muun local build-golangci"
	}
	return ""
}

func findRepoRoot() (string, error) {
	cmd := exec.CommandContext(
		context.Background(),
		"git", "rev-parse", "--show-toplevel",
	)
	out, err := cmd.Output()
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(out)), nil
}

// ComputeSourceChecksum returns the SHA-256 hex digest of the linter source
// inputs. Used by VerifyStaleness, cmd/checksum, and TestSourceChecksum.
func ComputeSourceChecksum(repoRoot string) (string, error) {
	lintersDir := filepath.Join(
		repoRoot, "libwallet", "linters",
	)

	var paths []string
	err := filepath.Walk(
		lintersDir,
		// we will filter out non-go files, test data files, cmd files
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
