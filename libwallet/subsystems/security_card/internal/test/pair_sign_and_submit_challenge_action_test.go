package test

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/app_provided_data"
	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/action"
	domain_model "github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// staleChallengeAge is well past the time a stored pair challenge stays fresh.
const staleChallengeAge = 10 * time.Minute

func TestPairSignAndSubmitChallengeRequestsAChallengeWhenNoneIsStored(t *testing.T) {
	// With no challenge stored, the flow asks Houston for one before the tap,
	// and says so, so the caller can tell the user to wait before holding the
	// card. The pairing consumes the challenge, so none is left stored.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpChallengePairFlow(t, generation)

		_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)
		require.NoError(t, err)

		require.Equal(t, 1, flow.requestChallenge.runs)
		require.Equal(
			t,
			[]sc_model.PairProgress{
				sc_model.PairProgressRefreshingChallenge,
				sc_model.PairProgressChallengeSigned,
			},
			flow.progress,
		)
		require.Equal(t, flow.mockCard.ConfirmedSecret(), flow.simulatedHouston.PairingSecret())

		pending, err := flow.protocolRepository.LoadPendingPairChallenge()
		require.NoError(t, err)
		require.Nil(t, pending)
	})
}

func TestPairSignAndSubmitChallengeSignsAStoredChallengeThatIsStillFresh(t *testing.T) {
	// A challenge requested ahead of the tap is what lets the tap start right
	// away, so the flow signs it instead of asking for another one.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpChallengePairFlow(t, generation)
		flow.storeChallenge(t)

		_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)
		require.NoError(t, err)

		require.Equal(t, 0, flow.requestChallenge.runs)
		require.Equal(
			t,
			[]sc_model.PairProgress{sc_model.PairProgressChallengeSigned},
			flow.progress,
		)
		require.Equal(t, flow.mockCard.ConfirmedSecret(), flow.simulatedHouston.PairingSecret())
	})
}

func TestPairSignAndSubmitChallengeRequestsANewChallengeWhenTheStoredOneIsStale(t *testing.T) {
	// Houston turns down a challenge it issued too long ago, so the flow asks
	// for a new one instead of spending the tap on one that cannot pair.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpChallengePairFlow(t, generation)
		flow.storeChallenge(t)
		require.NoError(t, flow.keyValueStorage.SaveBatch(map[string]any{
			storage.KeyPendingPairChallengeReceivedAtUnixMillis: time.Now().
				Add(-staleChallengeAge).
				UnixMilli(),
		}))

		_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)
		require.NoError(t, err)

		require.Equal(t, 1, flow.requestChallenge.runs)
		require.Equal(
			t,
			[]sc_model.PairProgress{
				sc_model.PairProgressRefreshingChallenge,
				sc_model.PairProgressChallengeSigned,
			},
			flow.progress,
		)
	})
}

func TestPairSignAndSubmitChallengeRecordsTheCardItPaired(t *testing.T) {
	// Houston holds only the pairing this flow made, so the record has to name
	// the card it paired. The sign flow pairs again only when the tapped card
	// is not the recorded one: a record left naming another card would keep
	// that card from pairing again, and it would no longer sign.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpChallengePairFlow(t, generation)
		require.NoError(t, flow.protocolRepository.SavePairedCard(
			domain_model.NewPairedCard("ffffffff", ""),
		))

		_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)
		require.NoError(t, err)

		pairAction := &countingPairAction{
			PairSecurityCardActionV3: action.NewPairSecurityCardActionV3(
				flow.card, flow.simulatedHouston,
			),
		}
		signAction := action.NewSignMessageSecurityCardActionV3(
			flow.card, flow.simulatedHouston, flow.protocolRepository, pairAction, flow.keyProvider,
		)
		require.NoError(t, signError(signAction.Run(t.Context())))
		require.Equal(t, 0, pairAction.runs)
	})
}

func TestPairSignAndSubmitChallengeReportsACardWithoutTheV3Applet(t *testing.T) {
	// A card that carries no V3 applet, such as one still on V2 firmware, has
	// nothing to pair with. The user can act on that, so the flow names it
	// rather than failing as an internal error, and keeps the challenge for
	// the next tap.
	flow := setUpChallengePairFlowOn(t, noV3AppletBridge{})

	_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)

	var appletNotFound *sc_model.MuunAppletNotFoundError
	require.ErrorAs(t, err, &appletNotFound)

	pending, err := flow.protocolRepository.LoadPendingPairChallenge()
	require.NoError(t, err)
	require.NotNil(t, pending)
}

func TestPairSignAndSubmitChallengeReportsACardWithNoPairingsLeft(t *testing.T) {
	// A card that handed out its last pairing refuses every pairing from then
	// on, and no pairing recovers it, so the user needs another card. The flow
	// has to say so rather than fail as an internal error. Firmware 3.0 does
	// not count its pairings, so only 3.1 runs out.
	flow := setUpChallengePairFlow(t, nfc.MockFirmwareGenerationV3_1)
	spendV3Pairings(t, flow.card, nfc.MaxPairingIndexV3_1+1)

	_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)

	var outOfPairings *sc_model.CardOutOfPairingsError
	require.ErrorAs(t, err, &outOfPairings)
}

func TestPairSignAndSubmitChallengeReportsAChallengeHoustonNoLongerAccepts(t *testing.T) {
	// Houston turns down a challenge it issued too long ago, even when the copy
	// stored here still looks fresh. The user can try again, so the flow names
	// it, and it drops the challenge so the next attempt asks for a new one.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpChallengePairFlow(t, generation)
		flow.storeChallenge(t)
		require.NoError(t, flow.keyValueStorage.SaveBatch(map[string]any{
			// When the mock issued its last challenge, in seconds.
			storage.KeyTimeSinceLastChallengeUnixMillis: time.Now().
				Add(-staleChallengeAge).
				Unix(),
		}))

		_, err := flow.pairAction.Run(t.Context(), flow.recordProgress)

		var expired *sc_model.ChallengeExpiredError
		require.ErrorAs(t, err, &expired)

		pending, err := flow.protocolRepository.LoadPendingPairChallenge()
		require.NoError(t, err)
		require.Nil(t, pending)
	})
}

// challengePairFlow is the challenge pair flow with the pieces a test has to
// drive or look into directly: the card and the server, to check they reached
// the same secret, the storage, to put the stored challenge in a state the
// flow would never leave it in, and the progress the flow reported.
type challengePairFlow struct {
	card               *nfc.MuunCardV3
	mockCard           *nfc.MockMuunCardV3
	keyProvider        *adapted_mock.MockKeyProvider
	simulatedHouston   *simulator.SimulatedHoustonService
	keyValueStorage    *storage.KeyValueStorage
	protocolRepository *sc_repository.ProtocolRepository
	requestChallenge   *countingPairRequestChallengeAction
	pairAction         action.PairSignAndSubmitChallengeAction
	progress           []sc_model.PairProgress
}

// storeChallenge requests a challenge ahead of the flow, as a caller does to
// have one ready before the user taps the card. It goes around the counter,
// which only counts the requests the flow makes on its own.
func (f *challengePairFlow) storeChallenge(t *testing.T) {
	t.Helper()

	require.NoError(t, f.requestChallenge.PairRequestChallengeAction.Run(t.Context()))
}

func (f *challengePairFlow) recordProgress(progress sc_model.PairProgress) error {
	f.progress = append(f.progress, progress)
	return nil
}

// countingPairRequestChallengeAction records how often the flow asked Houston
// for a challenge, which is what tells a stored challenge that was signed
// apart from one that was replaced: the pairing succeeds either way.
type countingPairRequestChallengeAction struct {
	action.PairRequestChallengeAction
	runs int
}

func (a *countingPairRequestChallengeAction) Run(ctx context.Context) error {
	a.runs++
	return a.PairRequestChallengeAction.Run(ctx)
}

func setUpChallengePairFlow(
	t *testing.T,
	generation nfc.MockFirmwareGenerationV3,
) *challengePairFlow {
	t.Helper()

	// The card binds the pairing to Muun's identity key, so it has to hold
	// the public half of the one the mock server signs with.
	mockCard, err := nfc.NewMockMuunCardV3(simulator.CosignerPubDev(), generation)
	require.NoError(t, err)

	flow := setUpChallengePairFlowOn(t, nfc.NewMockJavaCard(mockCard))
	flow.mockCard = mockCard

	return flow
}

func setUpChallengePairFlowOn(
	t *testing.T,
	bridge app_provided_data.NfcBridge,
) *challengePairFlow {
	t.Helper()

	card := nfc.NewCardV3(bridge)

	keyValueStorage := teststorage.NewTestKeyValueStorage(t)
	protocolRepository := sc_repository.NewProtocolRepository(keyValueStorage)
	// The Houston simulator reads the user's public key from the provider the sign
	// flow signs its approval with, as the V3 sign tests set it up.
	keyProvider := newUserKeyProvider(t, newBasePathUserKey(t))
	simulatedHouston := simulator.NewSimulatedHoustonService(keyValueStorage, keyProvider)
	requestChallenge := &countingPairRequestChallengeAction{
		PairRequestChallengeAction: action.NewPairRequestChallengeAction(
			protocolRepository, simulatedHouston,
		),
	}

	return &challengePairFlow{
		card:               card,
		keyProvider:        keyProvider,
		simulatedHouston:   simulatedHouston,
		keyValueStorage:    keyValueStorage,
		protocolRepository: protocolRepository,
		requestChallenge:   requestChallenge,
		pairAction: action.NewPairSignAndSubmitChallengeAction(
			action.NewPairLoadPersistedChallengeAction(protocolRepository),
			requestChallenge,
			action.NewPairSignChallengeAction(card),
			action.NewPairSubmitSolvedChallengeAction(protocolRepository, simulatedHouston),
		),
	}
}
