package emergency_kit

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render"
	"github.com/muun/libwallet/emergencykit"
)

// GeneratedEKPDF is a model including the verificationCode and version
type GeneratedEKPDF struct {
	VerificationCode string
	Version          int
	// Profiling holds per-stage timings and allocation stats for this render.
	Profiling *go_render.RenderProfiling
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
		return nil, errors.Errorf("failed to create directory: %w", err)
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

	startEmbed := time.Now()
	metadata, err := libwallet.CreateEmergencyKitMetadata(ekParams)
	if err != nil {
		return nil, errors.Errorf("GenerateEkHtml failed to create metadata: %w", err)
	}

	metadataBytes, err := json.Marshal(&metadata)
	if err != nil {
		return nil, errors.Errorf(
			"GenerateEkHtml failed to marshal %s: %w",
			string(metadataBytes),
			err,
		)
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

	result.Profiling.EmbedMetadataMs = time.Since(startEmbed).Milliseconds()

	return &GeneratedEKPDF{
		VerificationCode: result.VerificationCode,
		Version:          result.Version,
		Profiling:        result.Profiling,
	}, nil
}

func stripFilePrefix(path string) string {
	if len(path) > 7 && path[:7] == "file://" {
		return path[7:]
	}
	return path
}
