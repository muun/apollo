package debug

import (
	"archive/zip"
	"io"
	"os"
	"path/filepath"
)

// ZipDataDirAction zips all regular files in the libwallet data directory
// into a single archive for debug/diagnostic purposes.
type ZipDataDirAction struct {
	dataDir string
}

func NewZipDataDirAction(dataDir string) *ZipDataDirAction {
	return &ZipDataDirAction{dataDir: dataDir}
}

// Run creates a zip archive at outputPath containing all regular files
// in the data directory. Subdirectories are skipped.
func (a *ZipDataDirAction) Run(outputPath string) error {
	if err := os.MkdirAll(filepath.Dir(outputPath), 0o755); err != nil {
		return err
	}

	entries, err := os.ReadDir(a.dataDir)
	if err != nil {
		return err
	}

	outFile, err := os.Create(outputPath)
	if err != nil {
		return err
	}
	defer outFile.Close()

	zw := zip.NewWriter(outFile)
	defer zw.Close()

	for _, entry := range entries {
		if !entry.Type().IsRegular() {
			continue
		}

		name := entry.Name()
		err := addFileToZip(zw, filepath.Join(a.dataDir, name), name)
		if err != nil {
			return err
		}
	}

	return nil
}

func addFileToZip(zw *zip.Writer, filePath, entryName string) error {
	f, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer f.Close()

	w, err := zw.Create(entryName)
	if err != nil {
		return err
	}

	_, err = io.Copy(w, f)
	return err
}
