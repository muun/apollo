package debug_test

import (
	"archive/zip"
	"io"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/domain/action/debug"
)

func TestZipDataDirAction_Run(t *testing.T) {
	// Setup: create a data dir with some files and a subdirectory.
	dataDir := t.TempDir()

	writeFile := func(name string, content []byte) {
		t.Helper()

		require.NoError(t, os.WriteFile(
			filepath.Join(dataDir, name), content, 0o644,
		))
	}
	writeFile("wallet.db", []byte("db-content"))
	writeFile("wallet.db-wal", []byte("wal-content"))
	require.NoError(t, os.Mkdir(filepath.Join(dataDir, "subdir"), 0o755))
	writeFile(filepath.Join("subdir", "nested.txt"), []byte("nested"))

	outputPath := filepath.Join(t.TempDir(), "out", "data.zip")
	action := debug.NewZipDataDirAction(dataDir)

	require.NoError(t, action.Run(outputPath))

	// Verify zip contents.
	r, err := zip.OpenReader(outputPath)
	require.NoError(t, err)
	defer r.Close()

	files := make(map[string]string)
	for _, f := range r.File {
		rc, err := f.Open()
		require.NoError(t, err)
		buf, err := io.ReadAll(rc)
		require.NoError(t, err)
		rc.Close()
		files[f.Name] = string(buf)
	}

	require.Len(t, files, 2)
	require.Equal(t, "db-content", files["wallet.db"])
	require.Equal(t, "wal-content", files["wallet.db-wal"])
}

func TestZipDataDirAction_EmptyDir(t *testing.T) {
	dataDir := t.TempDir()
	outputPath := filepath.Join(t.TempDir(), "empty.zip")
	action := debug.NewZipDataDirAction(dataDir)

	require.NoError(t, action.Run(outputPath))

	r, err := zip.OpenReader(outputPath)
	require.NoError(t, err)
	defer r.Close()
	require.Empty(t, r.File)
}

func TestZipDataDirAction_OverwritesExistingZip(t *testing.T) {
	dataDir := t.TempDir()
	require.NoError(t, os.WriteFile(
		filepath.Join(dataDir, "wallet.db"), []byte("new-data"), 0o644,
	))

	outputPath := filepath.Join(t.TempDir(), "data.zip")

	// Write a stale zip first (simulates a previous run).
	require.NoError(t, os.WriteFile(outputPath, []byte("stale"), 0o644))

	action := debug.NewZipDataDirAction(dataDir)
	require.NoError(t, action.Run(outputPath))

	r, err := zip.OpenReader(outputPath)
	require.NoError(t, err)
	defer r.Close()

	require.Len(t, r.File, 1)
	rc, err := r.File[0].Open()
	require.NoError(t, err)
	buf, err := io.ReadAll(rc)
	require.NoError(t, err)
	rc.Close()

	require.Equal(t, "wallet.db", r.File[0].Name)
	require.Equal(t, "new-data", string(buf))
}

func TestZipDataDirAction_NonExistentDir(t *testing.T) {
	action := debug.NewZipDataDirAction(filepath.Join(t.TempDir(), "does-not-exist"))
	outputPath := filepath.Join(t.TempDir(), "out.zip")

	err := action.Run(outputPath)
	require.Error(t, err)
}
