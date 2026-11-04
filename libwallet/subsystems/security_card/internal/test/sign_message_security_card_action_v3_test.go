package test

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/go-errors/errors"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/app_provided_data"
	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/test/teststorage"
	"github.com/muun/libwallet/subsystems/security_card/internal/data"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/action"
	domain_model "github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// maxFailedChallengeAttempts caps the loop that spends the card's failure
// budget. The budget's size belongs to the firmware, and the V3 protocol
// tests pin it; these tests only need the card to reach the end of it.
const maxFailedChallengeAttempts = 64

func TestSignMessageSecurityCardV3ReportsACardThatMustPairAgain(t *testing.T) {
	// A card that spent its budget answers nothing until it is paired
	// again. That is something the user can act on, so the flow has to name
	// it instead of failing as one more internal error.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpV3SignFlow(t, generation)

		require.NoError(t, signError(flow.signAction.Run(t.Context())))
		spendFailureBudget(t, flow)

		err := signError(flow.signAction.Run(t.Context()))

		var mustPairAgain *sc_model.CardMustPairAgainError
		require.ErrorAs(t, err, &mustPairAgain)
	})
}

func TestSignMessageSecurityCardV3ReportsTheFirmwareOfTheCardThatSigned(t *testing.T) {
	// The firmware is what the native layer reports with each sign, so it is
	// the one the tapped card named, whichever generation that is.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpV3SignFlow(t, generation)

		cardSigned, err := flow.signAction.Run(t.Context())

		require.NoError(t, err)
		require.Equal(t, string(generation), cardSigned.FirmwareVersion)
	})
}

func TestSignMessageSecurityCardV3ReportsTheFirmwareOfACardThatFails(t *testing.T) {
	// A failure after the card named its firmware carries it for the native
	// layer to report, and still reads as the error the native layer acts on.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpV3SignFlow(t, generation)

		require.NoError(t, signError(flow.signAction.Run(t.Context())))
		spendFailureBudget(t, flow)

		err := signError(flow.signAction.Run(t.Context()))

		var tappedCard *sc_model.TappedCardError
		require.ErrorAs(t, err, &tappedCard)
		require.Equal(t, string(generation), tappedCard.FirmwareVersion)
		var mustPairAgain *sc_model.CardMustPairAgainError
		require.ErrorAs(t, err, &mustPairAgain)
	})
}

func TestSignMessageSecurityCardV3ReportsNoFirmwareForACardThatNamedNone(t *testing.T) {
	// A card without the applet fails before naming any firmware, so there
	// is none to report.
	flow := setUpV3SignFlowOn(t, noV3AppletBridge{})

	err := signError(flow.signAction.Run(t.Context()))

	var tappedCard *sc_model.TappedCardError
	require.NotErrorAs(t, err, &tappedCard)
}

func TestSignMessageSecurityCardV3SignsAgainAfterANewPairing(t *testing.T) {
	// Pairing is the way out of a spent budget, so signing has to work
	// right after one, with no other recovery step.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpV3SignFlow(t, generation)

		require.NoError(t, signError(flow.signAction.Run(t.Context())))
		spendFailureBudget(t, flow)

		_, err := flow.pairAction.Run(t.Context())
		require.NoError(t, err)

		require.NoError(t, signError(flow.signAction.Run(t.Context())))
	})
}

func TestSignMessageSecurityCardV3ReportsAFirmwareItDoesNotKnow(t *testing.T) {
	// A card whose applet answers its selection in a shape this client cannot
	// read is a firmware newer than the app, and the user can act on that by
	// updating. The flow opens its session before it does anything else, so
	// this is where such a card is turned away, and it has to reach the caller
	// as itself rather than as one more internal failure.
	flow := setUpV3SignFlowOn(t, unknownFirmwareBridge{})

	err := signError(flow.signAction.Run(t.Context()))

	var unsupportedVersion *sc_model.UnsupportedCardVersionError
	require.ErrorAs(t, err, &unsupportedVersion)
	var pairInternal *sc_model.PairInternalError
	require.NotErrorAs(t, err, &pairInternal)
}

// unknownFirmwareBridge answers the selection with a shape no V3 firmware
// generation sends: not empty, not the four bytes of a firmware version, and
// not the sixteen of a version and a short device id. Nothing past the
// selection is ever reached.
type unknownFirmwareBridge struct{}

// statusWordOk is ISO 7816-4's success status word. The nfc package keeps it
// unexported, out of reach from here.
const statusWordOk = 0x9000

func (unknownFirmwareBridge) Transmit(_ []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return &app_provided_data.NfcBridgeResponse{
		Response:   []byte{1, 2, 3, 4, 5},
		StatusCode: statusWordOk,
	}, nil
}

func TestSignMessageSecurityCardV3ReportsACardWithoutTheV3Applet(t *testing.T) {
	// A card that carries no V3 applet, such as one still on V2 firmware, fails
	// its selection. The flow has nothing to pair with, so it has to report the
	// missing applet instead of trying.
	flow := setUpV3SignFlowOn(t, noV3AppletBridge{})

	err := signError(flow.signAction.Run(t.Context()))

	var appletNotFound *sc_model.MuunAppletNotFoundError
	require.ErrorAs(t, err, &appletNotFound)
	require.Equal(t, 0, flow.pairAction.runs)
}

// noV3AppletBridge stands in for a card that carries no V3 applet: it answers
// every command with ISO 7816-4's "file or application not found".
type noV3AppletBridge struct{}

const statusWordAppletNotFound = 0x6A82

func (noV3AppletBridge) Transmit(_ []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return &app_provided_data.NfcBridgeResponse{StatusCode: statusWordAppletNotFound}, nil
}

func TestSignMessageSecurityCardV3ReportsACardWithNoPairingsLeft(t *testing.T) {
	// A card that handed out its last pairing refuses every pairing from then
	// on, and no pairing recovers it, so the user needs another card. That is
	// something they can act on, and it has to reach them as itself rather
	// than as an internal failure of the pairing the sign ran on its own.
	// Firmware 3.0 does not count its pairings, so only 3.1 runs out. The
	// pairings go straight to the card: the flow would record each one as
	// the wallet's.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	spendV3Pairings(t, flow.card, nfc.MaxPairingIndexV3_1+1)

	err := signError(flow.signAction.Run(t.Context()))

	var outOfPairings *sc_model.CardOutOfPairingsError
	require.ErrorAs(t, err, &outOfPairings)
	var pairInternal *sc_model.PairInternalError
	require.NotErrorAs(t, err, &pairInternal)
}

func TestSignMessageSecurityCardV3PairsOnceWithACardThatDoesNotChange(t *testing.T) {
	// The card the flow paired with is the card still on the reader, so the
	// second signature has no reason to pair again. Whether the card
	// reports a short device id or only its version is the generation's.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		flow := setUpV3SignFlow(t, generation)

		require.NoError(t, signError(flow.signAction.Run(t.Context())))
		require.NoError(t, signError(flow.signAction.Run(t.Context())))

		require.Equal(t, 1, flow.pairAction.runs)
	})
}

// pairedVersionV2 is the marker a V2 pairing recorded in place of a version.
// Devices that paired a V2 card still hold it.
const pairedVersionV2 = "02XX"

func TestSignMessageSecurityCardV3PairsAgainWhenAV2CardIsRecorded(t *testing.T) {
	// No V3 card ever reports the marker a V2 pairing recorded, so the V3 flow
	// never takes it for its own.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_0)
	require.NoError(t, flow.protocolRepository.SavePairedCard(
		domain_model.NewPairedCard(pairedVersionV2, ""),
	))

	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	require.Equal(t, 1, flow.pairAction.runs)
}

// pairedShortDeviceIDKey is where the sign flow records the short device id
// of the card it paired with. A record written before the id existed holds
// the version alone, which is what deleting this key leaves.
const pairedShortDeviceIDKey = "securityCardPairedShortDeviceId"

func TestSignMessageSecurityCardV3KeepsAPairingRecordedBeforeTheShortDeviceID(t *testing.T) {
	// A 3.0 card reports no short device id, so a record with its version
	// alone still names it. An update must not make it pair again, which
	// would spend one of the pairings the card has for its life.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_0)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.NoError(t, flow.keyValueStorage.Delete(pairedShortDeviceIDKey))

	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	require.Equal(t, 1, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3PairsOnceMoreOnARecordWithoutTheShortDeviceID(t *testing.T) {
	// A 3.1 card reports a short device id that a record from before the id
	// lacks, so the flow cannot tell it is the paired card. It pairs once
	// more, records the card whole, and signs without pairing after that.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.NoError(t, flow.keyValueStorage.Delete(pairedShortDeviceIDKey))

	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.Equal(t, 2, flow.pairAction.runs)

	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.Equal(t, 2, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3PairsAgainWithAnotherCardOfTheSameFirmware(t *testing.T) {
	// Two cards of one firmware report the same version, so the short device
	// id is the only thing that tells them apart: the pairing the flow holds
	// is the first card's, and signing with it on the second would be refused.
	firstCard, err := nfc.NewMockMuunCardV3(
		simulator.CosignerPubDev(), nfc.MockFirmwareGenerationV3_1,
	)
	require.NoError(t, err)
	secondCard, err := nfc.NewMockMuunCardV3(
		simulator.CosignerPubDev(), nfc.MockFirmwareGenerationV3_1,
	)
	require.NoError(t, err)

	reader := &cardReader{bridge: nfc.NewMockJavaCard(firstCard)}
	flow := setUpV3SignFlowOn(t, reader)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	reader.bridge = nfc.NewMockJavaCard(secondCard)

	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.Equal(t, 2, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3SubmitsTheClientApproval(t *testing.T) {
	// Houston ties a solved challenge to the wallet through the client's
	// signature, so it has to verify under the key Houston derives from the
	// user's base public key, over the challenge the client was handed. The
	// message and the path are rebuilt here from the protocol rather than from
	// the client's code, which would agree with itself either way.
	// The reason is what the signature exists to bind, so the challenge
	// carries one: with the mock's empty reason, a client hashing the wrong
	// bytes would still pass.
	reason := []byte("Send 0.001 BTC to bcrt1qexample")
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	recorder := &reasonIssuingHouston{
		SimulatedHoustonService: flow.simulatedHouston, reason: reason,
	}
	signAction := action.NewSignMessageSecurityCardActionV3(
		flow.card, recorder, flow.protocolRepository, flow.pairAction, flow.keyProvider,
	)

	require.NoError(t, signError(signAction.Run(t.Context())))

	serverPublicKey, err := hex.DecodeString(recorder.challenge.ServerPubKeyInHex)
	require.NoError(t, err)
	reasonHash := sha256.Sum256(reason)
	toBeSigned := append([]byte("client-approval"), serverPublicKey...)
	toBeSigned = append(toBeSigned, reasonHash[:]...)
	digest := sha256.Sum256(toBeSigned)

	clientApprovalKey, err := flow.userKey.PublicKey().DeriveTo("m/schema:1'/recovery:1'/cards:5/0")
	require.NoError(t, err)
	clientApprovalPublicKey, err := clientApprovalKey.ECPubKey()
	require.NoError(t, err)

	clientSigBytes, err := hex.DecodeString(recorder.submit.ClientSigInHex)
	require.NoError(t, err)
	clientSig, err := schnorr.ParseSignature(clientSigBytes)
	require.NoError(t, err)

	require.True(t, clientSig.Verify(digest[:], clientApprovalPublicKey))
}

func TestSignMessageSecurityCardV3StopsBeforeTheTapWhenTheUserKeyIsUnavailable(t *testing.T) {
	// The client loads its approval key before it touches the card, so a key
	// it cannot load stops the flow before the card is paired or answers a
	// challenge for nothing.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	flow.keyProvider.EXPECT().WithUserPrivateKey(mock.Anything).Unset()
	flow.keyProvider.EXPECT().WithUserPrivateKey(mock.Anything).
		Return(errors.New("keystore locked"))

	err := signError(flow.signAction.Run(t.Context()))

	require.ErrorContains(t, err, "keystore locked")
	require.Zero(t, flow.pairAction.runs)
	require.Equal(t, flow.mockCard.ConfirmedSecret(), flow.mockCard.ProposedSecret())
}

func TestSignMessageSecurityCardV3SignsWithThePayloadOfTheTappedCard(t *testing.T) {
	// A wallet with more than one card paired gets one payload per card,
	// and only the tapped card's verifies on it: the id it reported at
	// SELECT is what picks it out.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	signAction := action.NewSignMessageSecurityCardActionV3(
		flow.card,
		twoCardHouston{
			HoustonService:          flow.simulatedHouston,
			otherShortDeviceIDInHex: anotherCardShortDeviceIDInHex,
		},
		flow.protocolRepository,
		flow.pairAction,
		flow.keyProvider,
	)

	require.NoError(t, signError(signAction.Run(t.Context())))
}

func TestSignMessageSecurityCardV3SignsWithTheOnlyPayloadThatNamesNoCard(t *testing.T) {
	// A card whose firmware reports no id owns the payload that names none:
	// every other payload belongs to a card that does report one.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_0)
	signAction := action.NewSignMessageSecurityCardActionV3(
		flow.card,
		twoCardHouston{
			HoustonService:          flow.simulatedHouston,
			otherShortDeviceIDInHex: anotherCardShortDeviceIDInHex,
		},
		flow.protocolRepository,
		flow.pairAction,
		flow.keyProvider,
	)

	require.NoError(t, signError(signAction.Run(t.Context())))
}

func TestSignMessageSecurityCardV3RefusesToGuessBetweenPayloadsThatNameNoCard(t *testing.T) {
	// Two payloads that name no card belong to two cards whose firmware
	// reports no id, and nothing tells which one is on the reader. Signing on
	// the wrong one would fail on the card, so the flow stops before it signs.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_0)
	signAction := action.NewSignMessageSecurityCardActionV3(
		flow.card,
		twoCardHouston{HoustonService: flow.simulatedHouston, otherShortDeviceIDInHex: ""},
		flow.protocolRepository,
		flow.pairAction,
		flow.keyProvider,
	)

	err := signError(signAction.Run(t.Context()))

	require.ErrorContains(t, err, "more than one payload for the tapped card")
}

func TestSignMessageSecurityCardV3ReportsAChallengeWithNoPayloadForTheTappedCard(t *testing.T) {
	// The record names the card on the reader, but Houston only paired
	// another one, so no payload names it and the flow cannot sign.
	firstCard, err := nfc.NewMockMuunCardV3(
		simulator.CosignerPubDev(), nfc.MockFirmwareGenerationV3_1,
	)
	require.NoError(t, err)
	secondCard, err := nfc.NewMockMuunCardV3(
		simulator.CosignerPubDev(), nfc.MockFirmwareGenerationV3_1,
	)
	require.NoError(t, err)

	reader := &cardReader{bridge: nfc.NewMockJavaCard(firstCard)}
	flow := setUpV3SignFlowOn(t, reader)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	secondVersion := secondCard.FirmwareVersion()
	secondShortDeviceID := secondCard.ShortDeviceID()
	require.NoError(t, flow.protocolRepository.SavePairedCard(domain_model.NewPairedCard(
		hex.EncodeToString(secondVersion[:]),
		hex.EncodeToString(secondShortDeviceID[:]),
	)))
	reader.bridge = nfc.NewMockJavaCard(secondCard)

	err = signError(flow.signAction.Run(t.Context()))

	require.ErrorContains(t, err, "no payload for the tapped card")
	require.Equal(t, 1, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3PairsAgainAfterAFirmwareUpgrade(t *testing.T) {
	// An upgrade drops the pairing the card held, so signing under the old
	// secret would be refused. It is the same card, keys included, and the
	// version it reports on the next selection is all that tells the flow.
	// This pairing is not optional: without it the signature below fails.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_1)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	flow.mockCard.UpgradeFirmware(3, 2)

	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.Equal(t, 2, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3PairsAgainAfterAReinstall(t *testing.T) {
	// A reinstall into the next generation gives the card new keys and a new
	// version. The version is what the flow notices, and the pairing it runs
	// is with the card's new keys.
	flow := setUpV3SignFlow(t, nfc.MockFirmwareGenerationV3_0)
	require.NoError(t, signError(flow.signAction.Run(t.Context())))

	flow.mockCard.ReinstallFirmware(nfc.MockFirmwareGenerationV3_1)

	require.NoError(t, signError(flow.signAction.Run(t.Context())))
	require.Equal(t, 2, flow.pairAction.runs)
}

func TestSignMessageSecurityCardV3ReportsACardThatReportsNoVersion(t *testing.T) {
	// Firmware older than the selection payload reports nothing. No card in
	// the field runs it, and a relay can make any card look like it by
	// dropping the payload, so the flow turns it away before it pairs, the
	// same way it turns away a firmware newer than the app.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		mockCard, err := nfc.NewMockMuunCardV3(simulator.CosignerPubDev(), generation)
		require.NoError(t, err)

		flow := setUpV3SignFlowOn(t, preVersionFirmwareBridge{
			bridge: nfc.NewMockJavaCard(mockCard),
		})

		err = signError(flow.signAction.Run(t.Context()))

		var unsupportedVersion *sc_model.UnsupportedCardVersionError
		require.ErrorAs(t, err, &unsupportedVersion)
		require.Equal(t, 0, flow.pairAction.runs)
	})
}

// anotherCardShortDeviceIDInHex names a card other than the one under test.
var anotherCardShortDeviceIDInHex = hex.EncodeToString(
	bytes.Repeat([]byte{0xEE}, nfc.ShortDeviceIDSizeV3),
)

// twoCardHouston answers a sign challenge with a payload for another card
// ahead of the one the mock issued, the way Houston does for a wallet with
// more than one card paired. The mock pairs one card, so this is the only
// way to reach that answer. otherShortDeviceIDInHex names the other card,
// empty for one whose firmware reports no id. Its payload carries a MAC its
// card would refuse, so picking it is a failure rather than a coincidence.
type twoCardHouston struct {
	houston.HoustonService
	otherShortDeviceIDInHex string
}

func (h twoCardHouston) SignRequestChallenge(
	request model.SignRequestChallengeJSON,
) (model.SignRequestChallengeResponseJSON, error) {
	response, err := h.HoustonService.SignRequestChallenge(request)
	if err != nil {
		return response, err
	}

	otherCard := response.PerCardPayloads[0]
	otherCard.ShortDeviceIDInHex = h.otherShortDeviceIDInHex
	otherCard.MacInHex = hex.EncodeToString(bytes.Repeat([]byte{0xEE}, nfc.MACSize))
	response.PerCardPayloads = append(
		[]model.SignChallengePerCardPayloadJSON{otherCard}, response.PerCardPayloads...,
	)
	return response, nil
}

// cardReader stands in for the reader, on which cards come and go: whatever
// card it holds at the time answers the next command.
type cardReader struct {
	bridge app_provided_data.NfcBridge
}

func (r *cardReader) Transmit(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return r.bridge.Transmit(apdu)
}

// preVersionFirmwareBridge strips the selection's payload and passes
// everything else to the card behind it, standing in for firmware from before
// the applet answered its selection with anything.
type preVersionFirmwareBridge struct {
	bridge app_provided_data.NfcBridge
}

func (b preVersionFirmwareBridge) Transmit(
	apdu []byte,
) (*app_provided_data.NfcBridgeResponse, error) {
	response, err := b.bridge.Transmit(apdu)
	if err != nil || !isSelectAPDU(apdu) {
		return response, err
	}

	return &app_provided_data.NfcBridgeResponse{StatusCode: response.StatusCode}, nil
}

// isSelectAPDU reports whether apdu is the ISO SELECT: CLA 0x00 INS 0xA4, where
// every V3 command carries CLA 0x80 instead. Both are named in the nfc package,
// out of reach from here.
func isSelectAPDU(apdu []byte) bool {
	return len(apdu) >= 2 && apdu[0] == 0x00 && apdu[1] == 0xA4
}

// v3SignFlow is the V3 sign action with the pieces a test has to drive
// directly: the card, to fail challenges the action would never send, the
// server, to issue them, and the storage, to put the record in a state the
// action would never leave it in.
type v3SignFlow struct {
	card               *nfc.MuunCardV3
	mockCard           *nfc.MockMuunCardV3
	simulatedHouston   *simulator.SimulatedHoustonService
	keyValueStorage    *storage.KeyValueStorage
	protocolRepository *sc_repository.ProtocolRepository
	pairAction         *countingPairAction
	userKey            *hd_keys.HDPrivateKey
	keyProvider        *adapted_mock.MockKeyProvider
	signAction         action.SignMessageSecurityCardActionV3
}

// countingPairAction records how often the sign flow paired on its own, which
// is what tells "paired once and kept it" apart from "paired on every sign".
// A sign that succeeds says nothing about that: one that paired needlessly
// succeeds too, and spends one of the pairings the card has for its life.
type countingPairAction struct {
	action.PairSecurityCardActionV3
	runs int
}

func (a *countingPairAction) Run(
	ctx context.Context,
) (*sc_model.SecurityCardPaired, error) {
	a.runs++
	return a.PairSecurityCardActionV3.Run(ctx)
}

func setUpV3SignFlow(t *testing.T, generation nfc.MockFirmwareGenerationV3) *v3SignFlow {
	t.Helper()

	// The card binds the pairing to Muun's identity key, so it has to hold
	// the public half of the one the mock server signs with.
	mockCard, err := nfc.NewMockMuunCardV3(simulator.CosignerPubDev(), generation)
	require.NoError(t, err)

	flow := setUpV3SignFlowOn(t, nfc.NewMockJavaCard(mockCard))
	flow.mockCard = mockCard

	return flow
}

func setUpV3SignFlowOn(t *testing.T, bridge app_provided_data.NfcBridge) *v3SignFlow {
	t.Helper()

	card := nfc.NewCardV3(bridge)

	// The apps hand libwallet the user's key at the base path, not the
	// master, so that is where this one sits too. The sign flow never asks
	// for the cosigner key. The Houston simulator reads the same provider for the
	// user's public key, which Houston proper holds since the first session.
	userKey := newBasePathUserKey(t)
	keyProvider := newUserKeyProvider(t, userKey)

	keyValueStorage := teststorage.NewTestKeyValueStorage(t)
	protocolRepository := sc_repository.NewProtocolRepository(keyValueStorage)
	simulatedHouston := simulator.NewSimulatedHoustonService(keyValueStorage, keyProvider)
	pairAction := &countingPairAction{
		PairSecurityCardActionV3: action.NewPairSecurityCardActionV3(card, simulatedHouston),
	}

	return &v3SignFlow{
		card:               card,
		simulatedHouston:   simulatedHouston,
		keyValueStorage:    keyValueStorage,
		protocolRepository: protocolRepository,
		pairAction:         pairAction,
		userKey:            userKey,
		keyProvider:        keyProvider,
		signAction: action.NewSignMessageSecurityCardActionV3(
			card, simulatedHouston, protocolRepository, pairAction, keyProvider,
		),
	}
}

// spendFailureBudget fails challenges until the card stops answering them.
// They go straight to the card because the action only ever sends challenges
// the card can verify.
func spendFailureBudget(t *testing.T, flow *v3SignFlow) {
	t.Helper()

	session, err := flow.card.Connect(t.Context())
	require.NoError(t, err)

	for range maxFailedChallengeAttempts {
		challenge := requestChallenge(t, flow.simulatedHouston)
		challenge.MAC[0] ^= 0xFF

		_, err := session.SignChallenge(t.Context(), challenge)
		require.Error(t, err)

		var cardError *nfc.CardError
		require.ErrorAs(t, err, &cardError)
		if cardError.Code == nfc.ErrCardMustPairAgain {
			return
		}
	}

	t.Fatal("the card answered every failed challenge without spending its budget")
}

func requestChallenge(
	t *testing.T,
	simulatedHouston *simulator.SimulatedHoustonService,
) *domain_model.SecurityCardSignChallengeV3 {
	t.Helper()

	response, err := simulatedHouston.SignRequestChallenge(model.SignRequestChallengeJSON{})
	require.NoError(t, err)
	require.Len(t, response.PerCardPayloads, 1)

	challenge, err := data.MapSecurityCardSignChallengeV3(response, response.PerCardPayloads[0])
	require.NoError(t, err)

	return challenge
}

// signError keeps only the error of a sign, for the tests that care whether the card signed
// and not which firmware it named.
func signError(_ *sc_model.SecurityCardSigned, err error) error {
	return err
}
