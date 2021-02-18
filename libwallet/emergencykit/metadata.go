package emergencykit

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"

	"github.com/pdfcpu/pdfcpu/pkg/api"
	"github.com/pdfcpu/pdfcpu/pkg/pdfcpu"
)

// MetadataReader can extract the metadata file from a PDF.
type MetadataReader struct {
	SrcFile string
}

// MetadataWriter can add the metadata file to a PDF.
type MetadataWriter struct {
	SrcFile string
	DstFile string
}

// Metadata holds the machine-readable data for an Emergency Kit.
type Metadata struct {
	Version           int            `json:"version"`
	BirthdayBlock     int            `json:"birthdayBlock"`
	EncryptedKeys     []*MetadataKey `json:"encryptedKeys"`
	OutputDescriptors []string       `json:"outputDescriptors"`
}

// MetadataKey holds an entry in the Metadata key array.
type MetadataKey struct {
	DhPubKey         string `json:"dhPubKey"`
	EncryptedPrivKey string `json:"encryptedPrivKey"`
	Salt             string `json:"salt"`
}

// The name for the embedded metadata file in the PDF document:
const metadataName = "metadata.json"

// Default configuration values copied from pdfcpu source code (some values are irrelevant to us):
var pdfConfig = &pdfcpu.Configuration{
	Reader15:          true,
	DecodeAllStreams:  false,
	ValidationMode:    pdfcpu.ValidationRelaxed,
	Eol:               pdfcpu.EolLF,
	WriteObjectStream: true,
	WriteXRefStream:   true,
	EncryptUsingAES:   true,
	EncryptKeyLength:  256,
	Permissions:       pdfcpu.PermissionsNone,
}

// HasMetadata returns whether the metadata is present (and alone) in SrcFile.
func (mr *MetadataReader) HasMetadata() (bool, error) {
	fs, err := api.ListAttachmentsFile(mr.SrcFile, pdfConfig)
	if err != nil {
		return false, fmt.Errorf("HasMetadata failed to list attachments: %w", err)
	}

	return len(fs) == 1 && fs[0] == metadataName, nil
}

// ReadMetadata returns the deserialized metadata file embedded in the SrcFile PDF.
func (mr *MetadataReader) ReadMetadata() (*Metadata, error) {
	// NOTE:
	// Due to library constraints, this makes use of a temporary directory in the default system temp
	// location, which for the Recovery Tool will always be accessible. If we eventually want to read
	// this metadata in mobile clients, we'll need the caller to provide a directory.

	// Before we begin, verify that the metadata file is embedded:
	hasMetadata, err := mr.HasMetadata()
	if err != nil {
		return nil, fmt.Errorf("ReadMetadata failed to check for existence: %w", err)
	}
	if !hasMetadata {
		return nil, fmt.Errorf("ReadMetadata didn't find %s (or found more) in this PDF", metadataName)
	}

	// Create the temporary directory, with a deferred call to clean up:
	tmpDir, err := ioutil.TempDir("", "ek-metadata-*")
	if err != nil {
		return nil, fmt.Errorf("ReadMetadata failed to create a temporary directory")
	}

	defer os.RemoveAll(tmpDir)

	// Extract the embedded attachment from the PDF into that directory:
	err = api.ExtractAttachmentsFile(mr.SrcFile, tmpDir, []string{metadataName}, pdfConfig)
	if err != nil {
		return nil, fmt.Errorf("ReadMetadata failed to extract attachment: %w", err)
	}

	// Read the contents of the file:
	metadataBytes, err := ioutil.ReadFile(filepath.Join(tmpDir, metadataName))
	if err != nil {
		return nil, fmt.Errorf("ReadMetadata failed to read the extracted file: %w", err)
	}

	// Deserialize the metadata:
	var metadata Metadata
	err = json.Unmarshal(metadataBytes, &metadata)
	if err != nil {
		return nil, fmt.Errorf("ReadMetadata failed to unmarshal %s: %w", string(metadataBytes), err)
	}

	// Done we are!
	return &metadata, nil
}

// WriteMetadata creates a copy of SrcFile with attached JSON metadata into DstFile.
func (mw *MetadataWriter) WriteMetadata(metadata *Metadata) error {
	// NOTE:
	// Due to library constraints, this makes use of a temporary file placed in the same directory as
	// `SrcFile`, which is assumed to be writable. This is a much safer bet than attempting to pick a
	// location for temporary files ourselves.

	// Decide the location of the temporary file:
	srcDir := filepath.Dir(mw.SrcFile)
	tmpFile := filepath.Join(srcDir, metadataName)

	// Serialize the metadata:
	metadataBytes, err := json.Marshal(metadata)
	if err != nil {
		return fmt.Errorf("WriteMetadata failed to marshal: %w", err)
	}

	// Write to the temporary file, with a deferred call to clean up:
	err = ioutil.WriteFile(tmpFile, metadataBytes, os.FileMode(0600))
	if err != nil {
		return fmt.Errorf("WriteMetadata failed to write a temporary file: %w", err)
	}

	defer os.Remove(tmpFile)

	// Add the attachment, returning potential errors:
	err = api.AddAttachmentsFile(mw.SrcFile, mw.DstFile, []string{tmpFile}, false, pdfConfig)
	if err != nil {
		return fmt.Errorf("WriteMetadata failed to add attachment file %s: %w", tmpFile, err)
	}

	return nil
}
