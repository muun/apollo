package presentation

import (
	libwallet "github.com/muun/libwallet/legacy"
	ek_model "github.com/muun/libwallet/subsystems/emergency_kit/presentation/model"
)

type EmergencyKit interface {
	GenerateEmergencyKitPDF(
		ekParams *libwallet.EKInput,
		outputPath string,
		language string,
	) (*ek_model.GeneratedEKPDF, error)
}
