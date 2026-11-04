package security_card

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/storage"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/action"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
	"github.com/muun/libwallet/subsystems/security_card/internal/presentation"
)

type SecurityCard = presentation.SecurityCard

func NewSecurityCard(
	nfcBridge app_provided_data.NfcBridge,
	keyValueStorage *storage.KeyValueStorage,
	houstonService houston.HoustonService,
	keyProvider adapted.KeyProvider,
) SecurityCard {
	muunCardV3 := nfc.NewCardV3(nfcBridge)
	protocolRepository := sc_repository.NewProtocolRepository(keyValueStorage)

	pairV3 := action.NewPairSecurityCardActionV3(muunCardV3, houstonService)
	pairRequestChallenge := action.NewPairRequestChallengeAction(
		protocolRepository, houstonService,
	)

	return presentation.NewSecurityCard(
		action.NewSignMessageSecurityCardActionV3(
			muunCardV3, houstonService, protocolRepository, pairV3, keyProvider,
		),
		pairRequestChallenge,
		action.NewPairSignAndSubmitChallengeAction(
			action.NewPairLoadPersistedChallengeAction(protocolRepository),
			pairRequestChallenge,
			action.NewPairSignChallengeAction(muunCardV3),
			action.NewPairSubmitSolvedChallengeAction(
				protocolRepository, houstonService,
			),
		),
	)
}

// NFC protocol re-export for the Houston simulator.
// TODO: remove it when we delete the simulator.

func ComputeHMACSHA256(key, inputData []byte) []byte {
	return nfc.ComputeHMACSHA256(key, inputData)
}
