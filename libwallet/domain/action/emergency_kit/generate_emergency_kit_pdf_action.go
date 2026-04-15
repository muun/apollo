package emergency_kit

import (
	"encoding/json"
	"fmt"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render"
	"github.com/muun/libwallet/emergencykit"
	"os"
	"path/filepath"
)

// GeneratedEKPDF is a model including the verificationCode and version
type GeneratedEKPDF struct {
	VerificationCode string
	Version          int
}

// GenerateEmergencyKitPDFAction action for generating emergency kit PDFs
type GenerateEmergencyKitPDFAction struct {
	// No dependencies needed for this action currently
}

func NewGenerateEmergencyKitPDFAction() *GenerateEmergencyKitPDFAction {
	return &GenerateEmergencyKitPDFAction{}
}

// Run generates an emergency kit PDF and returns a GeneratedEKPDF.
func (a *GenerateEmergencyKitPDFAction) Run(
	ekParams *libwallet.EKInput,
	outputPath string,
	language string,
) (*GeneratedEKPDF, error) {
	outputPath = stripFilePrefix(outputPath)

	outputDir := filepath.Dir(outputPath)
	err := os.MkdirAll(outputDir, 0755)
	if err != nil {
		return nil, fmt.Errorf("failed to create directory: %w", err)
	}

	ekInput := &emergencykit.Input{
		FirstEncryptedKey:  ekParams.FirstEncryptedKey,
		FirstFingerprint:   ekParams.FirstFingerprint,
		SecondEncryptedKey: ekParams.SecondEncryptedKey,
		SecondFingerprint:  ekParams.SecondFingerprint,
		Version:            libwallet.EkVersionCurrent,
	}

	preMetadataPath := outputPath + ".tmp"
	err = os.Remove(preMetadataPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	result, err := go_render.Render(ekInput, preMetadataPath, language)
	if err != nil {
		return nil, err
	}

	metadata, err := libwallet.CreateEmergencyKitMetadata(ekParams)
	if err != nil {
		return nil, fmt.Errorf("GenerateEkHtml failed to create metadata: %w", err)
	}

	metadataBytes, err := json.Marshal(&metadata)
	if err != nil {
		return nil, fmt.Errorf("GenerateEkHtml failed to marshal %s: %w", string(metadataBytes), err)
	}

	err = os.Remove(outputPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	srcPath := stripFilePrefix(result.Path)
	err = libwallet.AddEmergencyKitMetadata(string(metadataBytes), srcPath, outputPath)
	if err != nil {
		return nil, err
	}

	err = os.Remove(preMetadataPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	return &GeneratedEKPDF{
		VerificationCode: result.VerificationCode,
		Version:          result.Version,
	}, nil
}

func stripFilePrefix(path string) string {
	if len(path) > 7 && path[:7] == "file://" {
		return path[7:]
	}
	return path
}
