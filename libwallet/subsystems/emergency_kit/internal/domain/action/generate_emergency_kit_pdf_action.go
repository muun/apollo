package action

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"

	"github.com/go-errors/errors"

	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/emergencykit"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render"
	ek_model "github.com/muun/libwallet/subsystems/emergency_kit/presentation/model"
)

type GenerateEmergencyKitPDFAction interface {
	Run(
		ekParams *libwallet.EKInput,
		outputPath string,
		language string,
	) (*ek_model.GeneratedEKPDF, error)
}

type generateEmergencyKitPDFAction struct{}

func NewGenerateEmergencyKitPDFAction() GenerateEmergencyKitPDFAction {
	return &generateEmergencyKitPDFAction{}
}

func (a *generateEmergencyKitPDFAction) Run(
	ekParams *libwallet.EKInput,
	outputPath string,
	language string,
) (*ek_model.GeneratedEKPDF, error) {
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
		return nil, errors.Errorf(
			"GenerateEkHtml failed to create metadata: %w", err,
		)
	}

	metadataBytes, err := json.Marshal(&metadata)
	if err != nil {
		return nil, errors.Errorf("marshal metadata: %w", err)
	}

	err = os.Remove(outputPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	srcPath := stripFilePrefix(result.Path)
	err = libwallet.AddEmergencyKitMetadata(
		string(metadataBytes), srcPath, outputPath,
	)
	if err != nil {
		return nil, err
	}

	err = os.Remove(preMetadataPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	result.Profiling.EmbedMetadataMs = time.Since(startEmbed).Milliseconds()

	if finalInfo, err := os.Stat(outputPath); err == nil {
		result.Profiling.KitSizeBytes = finalInfo.Size()
	}

	return &ek_model.GeneratedEKPDF{
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
