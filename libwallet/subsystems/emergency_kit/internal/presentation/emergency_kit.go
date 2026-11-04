package presentation

import (
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/action"
	ek_model "github.com/muun/libwallet/subsystems/emergency_kit/presentation/model"
)

type EmergencyKit struct {
	generateEmergencyKitPDF action.GenerateEmergencyKitPDFAction
}

func NewEmergencyKit(
	generateEmergencyKitPDF action.GenerateEmergencyKitPDFAction,
) EmergencyKit {
	return EmergencyKit{
		generateEmergencyKitPDF: generateEmergencyKitPDF,
	}
}

func (e *EmergencyKit) GenerateEmergencyKitPDF(
	ekParams *libwallet.EKInput,
	outputPath string,
	language string,
) (*ek_model.GeneratedEKPDF, error) {
	return e.generateEmergencyKitPDF.Run(ekParams, outputPath, language)
}
