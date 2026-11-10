package emergency_kit

import (
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/action"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/presentation"
)

type EmergencyKit = presentation.EmergencyKit

func NewEmergencyKit() EmergencyKit {
	return presentation.NewEmergencyKit(
		action.NewGenerateEmergencyKitPDFAction(),
	)
}
