package test

import (
	"encoding/binary"
	"testing"

	"github.com/fxamacker/cbor/v2"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// The mock stands in for the firmware in every V3 test, so what it answers on
// the paths a client can hit has to match the card: what SELECT carries back,
// and the status words a refused PAIR gets, in the order the card checks.

func TestMockMuunCardV3_SelectWithoutLeAnswersNoData(t *testing.T) {
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	response, err := bridge.Transmit(selectMuunCardV3APDU(t, 0))
	require.NoError(t, err)

	require.Equal(t, int32(nfc.ResponseOK), response.StatusCode)
	require.Empty(t, response.Response)
}

func TestMockMuunCardV3_SelectWithLeAnswersVersionAndShortDeviceID(t *testing.T) {
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	// Room for 256 bytes, which is more than the whole answer needs.
	response, err := bridge.Transmit(
		selectMuunCardV3APDU(t, nfc.MaxShortAPDUResponseSize),
	)
	require.NoError(t, err)

	shortDeviceID := card.ShortDeviceID()
	require.Equal(t, int32(nfc.ResponseOK), response.StatusCode)
	require.Equal(
		t,
		append([]byte{0x00, 0x03, 0x00, 0x01}, shortDeviceID[:]...),
		response.Response,
	)
}

func TestMockMuunCardV3_SelectOnA30CardAnswersTheVersionAlone(t *testing.T) {
	// Firmware 3.0 is from before the short device id, so it answers with
	// the version and nothing after it, however much room the client made.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)

	response, err := bridge.Transmit(
		selectMuunCardV3APDU(t, nfc.MaxShortAPDUResponseSize),
	)
	require.NoError(t, err)

	require.Equal(t, int32(nfc.ResponseOK), response.StatusCode)
	require.Equal(t, []byte{0x00, 0x03, 0x00, 0x00}, response.Response)
}

func TestMockMuunCardV3_SelectWithShortLeAnswersNoData(t *testing.T) {
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	// Fifteen bytes cannot hold the sixteen-byte answer, and part of it is
	// worse than none.
	response, err := bridge.Transmit(selectMuunCardV3APDU(t, 15))
	require.NoError(t, err)

	require.Equal(t, int32(nfc.ResponseOK), response.StatusCode)
	require.Empty(t, response.Response)
}

func TestMockMuunCardV3_ShortDeviceIDMatchesTheCertificate(t *testing.T) {
	// The short device id is device_model_id || key_agreement_root[-4:], and
	// the certificate the card hands back attests both, as its hwmodel and
	// ueid claims. Reading them out of the certificate, rather than asking the
	// card, is what pins the derivation.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	session, err := nfc.NewCardV3(bridge).Connect(t.Context())
	require.NoError(t, err)

	server, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	pairing, err := session.Pair(t.Context(), server.PublicKey)
	require.NoError(t, err)

	// metadata_version(4) || firmware_version(4) || len(vendor_certificate)(2)
	// || vendor_certificate || key_agreement_pub(33) || device_status(1)
	lengthOffset := 2 * nfc.VersionSizeV3
	certificateLength := int(binary.BigEndian.Uint16(pairing.Metadata[lengthOffset:]))
	certificate := pairing.Metadata[lengthOffset+2 : lengthOffset+2+certificateLength]

	var envelope []cbor.RawMessage // [protected, unprotected, payload, signature]
	require.NoError(t, cbor.Unmarshal(certificate, &envelope))
	var payload []byte
	require.NoError(t, cbor.Unmarshal(envelope[2], &payload))
	var claims struct {
		UEID          []byte `cbor:"256,keyasint"`
		HardwareModel []byte `cbor:"259,keyasint"`
	}
	require.NoError(t, cbor.Unmarshal(payload, &claims))

	expected := append([]byte{}, claims.HardwareModel...)
	expected = append(expected, claims.UEID[len(claims.UEID)-4:]...)

	selectResponse := session.SelectResponse()
	require.NotNil(t, selectResponse)
	require.NotNil(t, selectResponse.ShortDeviceID)
	require.Equal(t, expected, selectResponse.ShortDeviceID[:])
}

func TestMockMuunCardV3_UpgradingTheFirmwareReportsTheNewVersion(t *testing.T) {
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	card.UpgradeFirmware(3, 2)

	response, err := bridge.Transmit(
		selectMuunCardV3APDU(t, nfc.MaxShortAPDUResponseSize),
	)
	require.NoError(t, err)

	require.Len(t, response.Response, nfc.SelectResponseWithDeviceIDSizeV3)
	require.Equal(t, []byte{0x00, 0x03, 0x00, 0x02}, response.Response[:nfc.VersionSizeV3])
}

func TestMockMuunCardV3_UpgradingTheFirmwareKeepsTheShortDeviceID(t *testing.T) {
	// An upgrade replaces MuunCardV3 alone, and the metadata applet keeps
	// what the ceremony stored.
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)
	shortDeviceID := card.ShortDeviceID()

	card.UpgradeFirmware(3, 2)

	response, err := bridge.Transmit(
		selectMuunCardV3APDU(t, nfc.MaxShortAPDUResponseSize),
	)
	require.NoError(t, err)

	require.Len(t, response.Response, nfc.SelectResponseWithDeviceIDSizeV3)
	require.Equal(t, shortDeviceID[:], response.Response[nfc.VersionSizeV3:])
}

func TestMockMuunCardV3_UpgradingTheFirmwareLeavesTheCardUnpaired(t *testing.T) {
	// The pairing lives in the upgradeable applet, so it does not survive an
	// upgrade, while the metadata applet keeps the keys and the certificate it
	// has always held. A client that went on signing under the old secret
	// would be refused, so the mock has to drop it too.
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)
	unpaired := make([]byte, len(card.ConfirmedSecret()))
	pairMockMuunCardV3(t, bridge)
	require.NotEqual(t, unpaired, card.ConfirmedSecret())

	card.UpgradeFirmware(3, 2)

	require.Equal(t, unpaired, card.ConfirmedSecret())
}

func TestMockMuunCardV3_UpgradingToAnOlderFirmwarePanics(t *testing.T) {
	_, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	require.PanicsWithError(
		t,
		"firmware 3.0 is older than the 3.1 the card runs",
		func() { card.UpgradeFirmware(3, 0) },
	)
}

func TestMockMuunCardV3_UpgradingToTheSameFirmwareIsAllowed(t *testing.T) {
	_, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)

	require.NotPanics(t, func() { card.UpgradeFirmware(3, 0) })
}

func TestMockMuunCardV3_UpgradingIntoTheNextGenerationPanics(t *testing.T) {
	// Firmware 3.1 brings its own metadata applet, which replacing MuunCardV3
	// alone cannot install.
	_, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)

	require.PanicsWithError(
		t,
		"firmware 3.1 does not run on a 3.0 metadata applet: reinstall the card instead",
		func() { card.UpgradeFirmware(3, 1) },
	)
}

func TestMockMuunCardV3_ReinstallingAnotherGenerationGivesTheCardANewIdentity(t *testing.T) {
	// Another generation is another metadata applet, and installing one
	// erases the keys and certificates the previous held: the card comes
	// out of its new ceremony as a card nobody has seen before, running the
	// version that introduced the generation.
	_, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)
	shortDeviceID := card.ShortDeviceID()
	commitmentPublicKey := card.CommitmentPublicKey()

	card.ReinstallFirmware(nfc.MockFirmwareGenerationV3_1)

	require.NotEqual(t, shortDeviceID, card.ShortDeviceID())
	require.NotEqual(t, commitmentPublicKey, card.CommitmentPublicKey())
	require.Equal(t, [nfc.VersionSizeV3]byte{0x00, 0x03, 0x00, 0x01}, card.FirmwareVersion())
}

func TestMockMuunCardV3_ReinstallingTheFirmwareLeavesTheCardUnpaired(t *testing.T) {
	// A reinstall replaces MuunCardV3 too, and with it the pairing.
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)
	unpaired := make([]byte, len(card.ConfirmedSecret()))
	pairMockMuunCardV3(t, bridge)
	require.NotEqual(t, unpaired, card.ConfirmedSecret())

	card.ReinstallFirmware(nfc.MockFirmwareGenerationV3_1)

	require.Equal(t, unpaired, card.ConfirmedSecret())
}

func TestMockMuunCardV3_PairingsAreNumberedFromZero(t *testing.T) {
	// The card starts with last_pairing_index at -1 and PAIR hands out the
	// index after it, so the first pairing gets 0 and the second 1.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	require.Equal(t, uint16(0), pairMockMuunCardV3(t, bridge).Index)
	require.Equal(t, uint16(1), pairMockMuunCardV3(t, bridge).Index)
}

func TestMockMuunCardV3_PairingsAreAllNamedZeroOn30(t *testing.T) {
	// Firmware 3.0 does not count: its pairing index is a constant.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_0)

	require.Equal(t, uint16(0), pairMockMuunCardV3(t, bridge).Index)
	require.Equal(t, uint16(0), pairMockMuunCardV3(t, bridge).Index)
}

func TestMockMuunCardV3_ReflashingTheFirmwareKeepsCountingPairings(t *testing.T) {
	// The count lives in the metadata applet, so an upgrade of MuunCardV3
	// alone does not hand the card its pairings back.
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)
	require.Equal(t, uint16(0), pairMockMuunCardV3(t, bridge).Index)

	card.UpgradeFirmware(3, 2)

	require.Equal(t, uint16(1), pairMockMuunCardV3(t, bridge).Index)
}

func TestMockMuunCardV3_PairRefusesOncePairingsAreSpent(t *testing.T) {
	// The budget is the card's for its life: the pairing after the last one
	// is refused, and so is every one after a reflash, since the count lives
	// in the metadata applet. Indexes run from 0, so the last pairing is
	// one more than the last index.
	bridge, card := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	for range nfc.MaxPairingIndexV3_1 + 1 {
		pairMockMuunCardV3(t, bridge)
	}

	server, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	response, err := bridge.Transmit(pairMuunCardV3APDU(server.PublicKey))
	require.NoError(t, err)
	require.Equal(t, int32(nfc.SWMuuncardV3TooManyPairings), response.StatusCode)

	card.UpgradeFirmware(3, 2)

	response, err = bridge.Transmit(pairMuunCardV3APDU(server.PublicKey))
	require.NoError(t, err)
	require.Equal(t, int32(nfc.SWMuuncardV3TooManyPairings), response.StatusCode)
}

func TestMockMuunCardV3_GetMetadataStaysOnTheFirstKey(t *testing.T) {
	// PAIR moves to the next key agreement key every 16 pairings, but
	// GET_METADATA describes key 0 for the card's whole life, as the
	// firmware's METADATA_KEY_INDEX does.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)

	before, err := bridge.Transmit(getMetadataMuunCardV3APDU())
	require.NoError(t, err)

	for range nfc.MaxUsesPerKeyV3 + 1 {
		pairMockMuunCardV3(t, bridge)
	}

	after, err := bridge.Transmit(getMetadataMuunCardV3APDU())
	require.NoError(t, err)
	require.Equal(t, before.Response, after.Response)
}

func TestMockMuunCardV3_SignChallengeRefusesAnotherPairingsIndex(t *testing.T) {
	// The card holds one pairing, so a challenge naming any other index is
	// one for a pairing it does not have, and answers as unpaired.
	bridge, _ := newMockMuunCardV3Bridge(t, nfc.MockFirmwareGenerationV3_1)
	pairing := pairMockMuunCardV3(t, bridge)

	challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)
	data := nfc.BuildSignChallengeDataV3(
		challengeKeyPair.PublicKey, 1, pairing.Index+1, 0, nil, make([]byte, nfc.MACSize),
	)

	response, err := bridge.Transmit(signChallengeMuunCardV3APDU(data))
	require.NoError(t, err)

	require.Equal(t, int32(nfc.SWMuuncardV3SlotNotPaired), response.StatusCode)
}

func TestMockMuunCardV3_PairRejectsWrongLength(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, _ := newMockMuunCardV3Bridge(t, generation)

		response, err := bridge.Transmit(pairMuunCardV3APDU(make([]byte, nfc.Secp256r1PointSize-1)))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3WrongLength), response.StatusCode)
	})
}

func TestMockMuunCardV3_PairRejectsOffCurvePoint(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, _ := newMockMuunCardV3Bridge(t, generation)

		offCurve := make([]byte, nfc.Secp256r1PointSize)
		offCurve[0] = 0x04

		response, err := bridge.Transmit(pairMuunCardV3APDU(offCurve))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3InvalidPubKey), response.StatusCode)
	})
}

func TestMockMuunCardV3_PairRefusesACardThatIsNotReady(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, card := newMockMuunCardV3Bridge(t, generation)
		card.SetDeviceStatus(nfc.DeviceStatusMissingCertV3)

		server, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)

		response, err := bridge.Transmit(pairMuunCardV3APDU(server.PublicKey))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3NotReady), response.StatusCode)
	})
}

func TestMockMuunCardV3_PairChecksLengthBeforeStatus(t *testing.T) {
	// The order MuunCardV3.pair uses: a wrong length answers 0x6700 even on a
	// card that would refuse the pairing anyway.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, card := newMockMuunCardV3Bridge(t, generation)
		card.SetDeviceStatus(nfc.DeviceStatusMissingCertV3)

		response, err := bridge.Transmit(pairMuunCardV3APDU(make([]byte, nfc.Secp256r1PointSize-1)))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3WrongLength), response.StatusCode)
	})
}

func TestMockMuunCardV3_SignChallengeRefusesACardThatIsNotReady(t *testing.T) {
	// A card that left READY answers no challenge, even for the pairing it
	// made while it was READY.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, card := newMockMuunCardV3Bridge(t, generation)
		pairing := pairMockMuunCardV3(t, bridge)
		card.SetDeviceStatus(nfc.DeviceStatusNukedV3)

		challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)
		data := nfc.BuildSignChallengeDataV3(
			challengeKeyPair.PublicKey, 1, pairing.Index, 0, nil, make([]byte, nfc.MACSize),
		)

		response, err := bridge.Transmit(signChallengeMuunCardV3APDU(data))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3NotReady), response.StatusCode)
	})
}

func TestMockMuunCardV3_SignChallengeChecksStatusBeforePairing(t *testing.T) {
	// The order MuunCardV3.signChallenge uses: a card that is not READY
	// answers NOT_READY even when it holds no pairing to answer NOT_PAIRED
	// for.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, card := newMockMuunCardV3Bridge(t, generation)
		card.SetDeviceStatus(nfc.DeviceStatusMissingCertV3)

		challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)
		data := nfc.BuildSignChallengeDataV3(
			challengeKeyPair.PublicKey, 1, 0, 0, nil, make([]byte, nfc.MACSize),
		)

		response, err := bridge.Transmit(signChallengeMuunCardV3APDU(data))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3NotReady), response.StatusCode)
	})
}

func TestMockMuunCardV3_SignChallengeRefusesALengthTheFirmwareReadsAsNegative(t *testing.T) {
	// The firmware reads reason_len as a signed short and answers
	// WRONG_LENGTH when it is negative, so 32768 and up never open a stream
	// on a card. The client caps reasons below that, so this one is built
	// here: the mock is what would catch that cap going away.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		bridge, _ := newMockMuunCardV3Bridge(t, generation)

		// reason_len is read after the pairing check, so the challenge only
		// reaches it on a paired card.
		session, err := nfc.NewCardV3(bridge).Connect(t.Context())
		require.NoError(t, err)

		serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)

		_, err = session.Pair(t.Context(), serverKeyPair.PublicKey)
		require.NoError(t, err)

		challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)

		data := make([]byte, nfc.SignChallengeHeaderSizeV3+nfc.MACSize)
		copy(data, challengeKeyPair.PublicKey)
		binary.BigEndian.PutUint16(
			data[nfc.Secp256r1PointSize+nfc.CallCounterSizeV3+nfc.PairIndexSize:],
			uint16(nfc.MaxReasonSizeV3+1),
		)

		response, err := bridge.Transmit(signChallengeMuunCardV3APDU(data))
		require.NoError(t, err)

		require.Equal(t, int32(nfc.SWMuuncardV3WrongLength), response.StatusCode)
	})
}

//==========================
// Private, helper functions
//==========================

func newMockMuunCardV3Bridge(
	t *testing.T,
	generation nfc.MockFirmwareGenerationV3,
) (*nfc.MockJavaCard, *nfc.MockMuunCardV3) {
	t.Helper()

	muun, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	card, err := nfc.NewMockMuunCardV3(muun.PublicKey, generation)
	require.NoError(t, err)

	return nfc.NewMockJavaCard(card), card
}

// pairMockMuunCardV3 pairs the card behind the bridge against a throwaway
// server key, and returns what PAIR answered: enough to check what an install
// does to a live pairing, or what index the pairing got, since nothing here
// verifies the pairing.
func pairMockMuunCardV3(t *testing.T, bridge *nfc.MockJavaCard) *nfc.PairingResponseV3 {
	t.Helper()

	session, err := nfc.NewCardV3(bridge).Connect(t.Context())
	require.NoError(t, err)

	server, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	pairing, err := session.Pair(t.Context(), server.PublicKey)
	require.NoError(t, err)
	return pairing
}

func selectMuunCardV3APDU(t *testing.T, maxResponseSize int) []byte {
	t.Helper()

	apdu, err := nfc.NewSelectAPDU(nfc.MuuncardV3AppletID, maxResponseSize)
	require.NoError(t, err)
	return apdu.SerializeShort()
}

func pairMuunCardV3APDU(data []byte) []byte {
	apdu := nfc.NewAPDU(nfc.CLAEdge, nfc.INSMuuncardV3Pair, nfc.NullByte, nfc.NullByte, data)
	return apdu.SerializeExtended()
}

func getMetadataMuunCardV3APDU() []byte {
	return nfc.NewAPDU(
		nfc.CLAEdge, nfc.INSMuuncardV3GetMetadata, nfc.NullByte, nfc.NullByte, nil,
	).SerializeShort()
}

func signChallengeMuunCardV3APDU(data []byte) []byte {
	apdu := nfc.NewAPDU(
		nfc.CLAEdge, nfc.INSMuuncardV3SignChallenge, nfc.NullByte, nfc.NullByte, data,
	)
	return apdu.SerializeShort()
}
