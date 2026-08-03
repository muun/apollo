package linters_test

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/muun/libwallet/linters"
)

// TestSourceChecksum verifies that .checksum matches the current linter sources.
// A mismatch means the linter source changed and the custom golangci-lint binary must be rebuilt.
func TestSourceChecksum(t *testing.T) {
	repoRoot := gitRoot(t)

	got, err := linters.ComputeSourceChecksum(repoRoot)
	if err != nil {
		t.Fatal("failed to compute checksum:", err)
	}

	checksumFile := filepath.Join(
		repoRoot, "libwallet", "linters",
		"muun_model_constructor", ".checksum",
	)
	stored, err := os.ReadFile(checksumFile)
	if err != nil {
		t.Fatalf("cannot read .checksum: %v", err)
	}
	want := strings.TrimSpace(string(stored))

	if got != want {
		t.Fatalf(
			"linter source checksum mismatch (got %s, want %s).\n"+
				"The linter source changed. Rebuild the custom binary and update the checksum:\n"+
				"  muun local build-golangci\n"+
				"  # then update .checksum with the new checksum: %s",
			got, want, got,
		)
	}
}

// TestCmdChecksumInSync verifies that cmd/checksum (standalone, no linters import)
// produces the same result as linters.ComputeSourceChecksum. The two must stay in
// sync — cmd/checksum intentionally avoids importing the linters package to prevent
// Go build cache poisoning (see cmd/checksum/main.go).
func TestCmdChecksumInSync(t *testing.T) {
	repoRoot := gitRoot(t)

	fromLib, err := linters.ComputeSourceChecksum(repoRoot)
	if err != nil {
		t.Fatal("ComputeSourceChecksum:", err)
	}

	cmd := exec.CommandContext(
		context.Background(),
		"go", "run", "./cmd/checksum",
	)
	cmd.Dir = filepath.Join(repoRoot, "libwallet", "linters")
	out, err := cmd.Output()
	if err != nil {
		t.Fatal("go run ./cmd/checksum:", err)
	}
	fromCmd := strings.TrimSpace(string(out))

	if fromLib != fromCmd {
		t.Fatalf(
			"cmd/checksum disagrees with linters.ComputeSourceChecksum:\n"+
				"  lib: %s\n  cmd: %s\n"+
				"Keep the two implementations in sync.",
			fromLib, fromCmd,
		)
	}
}

func gitRoot(t *testing.T) string {
	t.Helper()
	cmd := exec.CommandContext(context.Background(), "git", "rev-parse", "--show-toplevel")
	out, err := cmd.Output()
	if err != nil {
		t.Fatal("cannot find git root:", err)
	}
	return strings.TrimSpace(string(out))
}
