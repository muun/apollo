package test

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/test/teststorage"
	"github.com/muun/libwallet/subsystems/security_card/internal/data"
	domain_model "github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// A V3 pairing has to line up byte for byte across two implementations that
// never share code: the card assembles a MAC over its metadata, and the
// server recomputes it after reading the card's key out of metadata it has
// to parse. These tests run the real client between them, so a
// disagreement anywhere in that chain fails here rather than on a card.
//
// Every test that pairs runs over every firmware generation. The generations
// differ in what the metadata carries and where the card's keys are, and the
// challenge flow builds on what the pairing left behind: the secret and the
// commitment key.

func TestMockCardV3PairSucceeds(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)

		response := pairV3CardWithHouston(t, card, simulatedHouston)

		require.False(t, response.IsCardAlreadyUsed)

		// Both sides reached the same secret, which is the point of the
		// flow: the card mixed its static key with the server's challenge
		// key, and the server recovered that static key out of the
		// metadata, wherever this generation carries it.
		require.Equal(t, mockCard.ConfirmedSecret(), simulatedHouston.PairingSecret())
	})
}

func TestMockCardV3PairRejectsTamperedMetadata(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)

		challenge, err := simulatedHouston.PairRequestChallenge()
		require.NoError(t, err)

		serverPublicKey, err := hex.DecodeString(challenge.ServerPubKeyInHex)
		require.NoError(t, err)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		pairing, err := session.Pair(t.Context(), serverPublicKey)
		require.NoError(t, err)

		// Flip the device status the card reported, the last byte in every
		// generation's layout. It is covered by the MAC through the
		// metadata's authentication form, so the server must refuse the
		// pairing even though every other field still lines up.
		tampered := append([]byte{}, pairing.Metadata...)
		tampered[len(tampered)-1] ^= 0xFF

		_, err = simulatedHouston.PairSubmitSignedChallenge(model.PairSubmitSignedChallengeJSON{
			CardPubKeyInHex: hex.EncodeToString(pairing.CardPublicKey),
			Index:           pairing.Index,
			MetadataInHex:   hex.EncodeToString(tampered),
			MacInHex:        hex.EncodeToString(pairing.MAC),
		})

		// Pinned to the MAC: asserting on any error would also pass when
		// some other check refuses the pairing first.
		var houstonError *houston.HoustonResponseError
		require.ErrorAs(t, err, &houstonError)
		require.Equal(t, houston.ErrInvalidMac, houstonError.ErrorCode)
	})
}

func TestMockCardV3PairRejectsMetadataNoGenerationReads(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)

		challenge, err := simulatedHouston.PairRequestChallenge()
		require.NoError(t, err)

		serverPublicKey, err := hex.DecodeString(challenge.ServerPubKeyInHex)
		require.NoError(t, err)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		pairing, err := session.Pair(t.Context(), serverPublicKey)
		require.NoError(t, err)

		// Drop the last byte, the device status in every generation's
		// layout, so no generation can read what is left.
		truncated := pairing.Metadata[:len(pairing.Metadata)-1]

		_, err = simulatedHouston.PairSubmitSignedChallenge(model.PairSubmitSignedChallengeJSON{
			CardPubKeyInHex: hex.EncodeToString(pairing.CardPublicKey),
			Index:           pairing.Index,
			MetadataInHex:   hex.EncodeToString(truncated),
			MacInHex:        hex.EncodeToString(pairing.MAC),
		})

		// No reading reached a MAC, so the refusal is about the metadata, and
		// it carries why each generation could not read it.
		var houstonError *houston.HoustonResponseError
		require.ErrorAs(t, err, &houstonError)
		require.Equal(t, houston.ErrUnknown, houstonError.ErrorCode)
		require.Equal(t, "invalid metadata", houstonError.Message)
		require.Contains(t, houstonError.DeveloperMessage, "3.0: ")
		require.Contains(t, houstonError.DeveloperMessage, "3.1: ")
	})
}

//==========================
// Private, helper functions
//==========================

func setUpV3Pairing(t *testing.T, generation nfc.MockFirmwareGenerationV3) (
	*nfc.MuunCardV3,
	*nfc.MockMuunCardV3,
	*simulator.SimulatedHoustonService,
) {
	t.Helper()

	// The card binds the pairing to Muun's identity key, so it has to hold
	// the public half of the one the mock server signs with.
	mockCard, err := nfc.NewMockMuunCardV3(simulator.CosignerPubDev(), generation)
	require.NoError(t, err)

	card := nfc.NewCardV3(nfc.NewMockJavaCard(mockCard))

	simulatedHouston := simulator.NewSimulatedHoustonService(
		teststorage.NewTestKeyValueStorage(t),
		newUserKeyProvider(t, v3TestUserKey(t)),
	)

	return card, mockCard, simulatedHouston
}

// signV3ClientApproval signs the client's approval of a challenge with the
// given user key, the way the sign action does, and returns it as the submit
// carries it.
func signV3ClientApproval(
	t *testing.T,
	userKey *hd_keys.HDPrivateKey,
	challenge *domain_model.SecurityCardSignChallengeV3,
) string {
	t.Helper()

	clientApprovalHDPrivateKey, err := userKey.DeriveTo(
		hd_keys.SecurityCardClientApprovalKeyPath,
	)
	require.NoError(t, err)
	clientApprovalECPrivateKey, err := clientApprovalHDPrivateKey.ECPrivKey()
	require.NoError(t, err)

	clientSig, err := musig.SignSchnorr(
		clientApprovalECPrivateKey.Serialize(), challenge.ClientApprovalDigest(),
	)
	require.NoError(t, err)

	return hex.EncodeToString(clientSig)
}

func pairV3CardWithHouston(
	t *testing.T,
	card *nfc.MuunCardV3,
	simulatedHouston *simulator.SimulatedHoustonService,
) model.PairSubmitSignedChallengeResponseJSON {
	t.Helper()

	challenge, err := simulatedHouston.PairRequestChallenge()
	require.NoError(t, err)

	serverPublicKey, err := hex.DecodeString(challenge.ServerPubKeyInHex)
	require.NoError(t, err)

	session, err := card.Connect(t.Context())
	require.NoError(t, err)

	pairing, err := session.Pair(t.Context(), serverPublicKey)
	require.NoError(t, err)

	response, err := simulatedHouston.PairSubmitSignedChallenge(
		model.PairSubmitSignedChallengeJSON{
			CardPubKeyInHex: hex.EncodeToString(pairing.CardPublicKey),
			Index:           pairing.Index,
			MetadataInHex:   hex.EncodeToString(pairing.Metadata),
			MacInHex:        hex.EncodeToString(pairing.MAC),
		},
	)
	require.NoError(t, err)

	return response
}

func TestMockCardV3PairRotatesTheKeyAgreementKeyEvery16Pairings(t *testing.T) {
	// The pairing with index n uses key agreement key n / 16, and the
	// metadata carries that key with its own certificate, so Houston reads a
	// certificate of a leaf other than 0 for the first time here. The MAC
	// proves only that the card used the private half of the key the
	// metadata carries in the clear, and the Houston simulator does not check the
	// certificate's inclusion proof, so each pairing's certificate is
	// compared with the one the card holds for that key.
	card, mockCard, simulatedHouston := setUpV3Pairing(t, nfc.MockFirmwareGenerationV3_1)
	certificateOfKey := func(keyIndex int) string {
		return hex.EncodeToString(mockCard.VendorCertificate(keyIndex))
	}

	response := pairV3CardWithHouston(t, card, simulatedHouston)
	require.Equal(t, certificateOfKey(0), response.SecurityCard.Metadata.VendorCertificateInHex)

	// Indexes 1 to 14 go nowhere, and index 15 still pairs under the first
	// key.
	spendV3Pairings(t, card, nfc.MaxUsesPerKeyV3-2)
	response = pairV3CardWithHouston(t, card, simulatedHouston)
	require.Equal(t, certificateOfKey(0), response.SecurityCard.Metadata.VendorCertificateInHex)
	require.Equal(t, mockCard.ConfirmedSecret(), simulatedHouston.PairingSecret())

	// Index 16 is the first under the second key.
	response = pairV3CardWithHouston(t, card, simulatedHouston)
	require.Equal(t, certificateOfKey(1), response.SecurityCard.Metadata.VendorCertificateInHex)
	require.Equal(t, mockCard.ConfirmedSecret(), simulatedHouston.PairingSecret())

	signV3ChallengeWithHouston(t, card, simulatedHouston)
}

func TestMockCardV3SignChallengeSucceeds(t *testing.T) {
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		signV3ChallengeWithHouston(t, card, simulatedHouston)

		// The ratchet moved the server to the secret the card proposed. The
		// card stays on its confirmed one until a later challenge arrives
		// under the proposal, which is what proves the server got it.
		require.Equal(t, mockCard.ProposedSecret(), simulatedHouston.PairingSecret())
		require.NotEqual(t, mockCard.ConfirmedSecret(), mockCard.ProposedSecret())
	})
}

func TestMockCardV3SignChallengeRatchetsBetweenChallenges(t *testing.T) {
	// The strongest thing this suite can assert: a second challenge only
	// works if the first one rotated the secret the same way on both sides.
	// A ratchet that diverges passes the first challenge and fails here.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		pairingSecret := append([]byte{}, mockCard.ConfirmedSecret()...)

		signV3ChallengeWithHouston(t, card, simulatedHouston)
		firstProposal := append([]byte{}, mockCard.ProposedSecret()...)
		require.NotEqual(t, pairingSecret, firstProposal)
		require.Equal(t, pairingSecret, mockCard.ConfirmedSecret())

		signV3ChallengeWithHouston(t, card, simulatedHouston)

		// The second challenge arrived under the first proposal, so the card
		// promoted it and proposed another.
		require.Equal(t, firstProposal, mockCard.ConfirmedSecret())
		require.NotEqual(t, firstProposal, mockCard.ProposedSecret())
		require.Equal(t, mockCard.ProposedSecret(), simulatedHouston.PairingSecret())
	})
}

func TestMockCardV3SignChallengeSignsTheApproval(t *testing.T) {
	// The card's commitment signature is what keeps the server from
	// fabricating an approval, so it has to verify under the card's own
	// commitment key, not under anything the server holds.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		challenge, response := signV3ChallengeWithHouston(t, card, simulatedHouston)
		require.NotEmpty(t, response.CommitmentSig)

		// TBS = "card-approval" || C || HASH(reason)
		reasonHash := sha256.Sum256(challenge.Reason)
		toBeSigned := append([]byte("card-approval"), challenge.ServerPublicKey...)
		toBeSigned = append(toBeSigned, reasonHash[:]...)

		verified, err := simulatedHouston.VerifySignature(
			mockCard.CommitmentPublicKey(),
			toBeSigned,
			response.CommitmentSig,
		)
		require.NoError(t, err)
		require.True(t, verified, "commitment signature must verify under the card's key")
	})
}

func TestMockCardV3SignChallengeRejectsAForgedApproval(t *testing.T) {
	// The server holds the card's secret, so it can produce the response MAC
	// on its own. The commitment signature is the one part it cannot forge,
	// which is why the submit has to reject a signature that does not verify
	// instead of relaying it.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		challenge := requestV3Challenge(t, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		response, err := session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)
		require.NotEmpty(t, response.CommitmentSig)

		// Flip the last byte of the signature. Everything else about the
		// response still lines up, the MAC included.
		forged := append([]byte{}, response.CommitmentSig...)
		forged[len(forged)-1] ^= 0xFF

		err = simulatedHouston.SignSubmitSignedChallenge(model.SignSubmitSignedChallengeJSON{
			ShortDeviceIDInHex: session.SelectResponse().ShortDeviceIDInHex(),
			CardPubKeyInHex:    hex.EncodeToString(response.CardPublicKey),
			MacInHex:           hex.EncodeToString(response.MAC),
			CommitSigInHex:     hex.EncodeToString(forged),
		})

		var houstonError *houston.HoustonResponseError
		require.ErrorAs(
			t, err, &houstonError, "a signature the card never made must not be accepted",
		)
		require.Equal(t, houston.ErrInvalidSignature, houstonError.ErrorCode)
	})
}

func TestMockHoustonV3RefusesAClientApprovalThatIsNotTheWallets(t *testing.T) {
	// The client's signature is what ties a solved challenge to the wallet,
	// so the mock has to refuse one made with another wallet's key or over
	// another challenge, and a submit that carries none. It refuses before
	// the ratchet, so the secret stays where it was.
	walletKey := v3TestUserKey(t)
	otherWalletKey := newBasePathUserKey(t)
	anotherServerKey, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	// Each case approves the challenge it was handed with something changed:
	// who signs, or which reason or server key the approval covers. A nil
	// signer submits no approval at all.
	cases := []struct {
		name            string
		signer          *hd_keys.HDPrivateKey
		reason          []byte
		serverPublicKey []byte
	}{
		{name: "missing"},
		{name: "made with another wallet's key", signer: otherWalletKey},
		{name: "over another reason", signer: walletKey, reason: []byte("another action")},
		{
			name:            "over another challenge",
			signer:          walletKey,
			serverPublicKey: anotherServerKey.PublicKey,
		},
	}

	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			card, _, simulatedHouston := setUpV3Pairing(t, nfc.MockFirmwareGenerationV3_1)
			pairV3CardWithHouston(t, card, simulatedHouston)
			secret := append([]byte{}, simulatedHouston.PairingSecret()...)

			challenge := requestV3Challenge(t, simulatedHouston)

			session, err := card.Connect(t.Context())
			require.NoError(t, err)

			response, err := session.SignChallenge(t.Context(), challenge)
			require.NoError(t, err)

			clientSigInHex := ""
			if testCase.signer != nil {
				serverPublicKey := challenge.ServerPublicKey
				if testCase.serverPublicKey != nil {
					serverPublicKey = testCase.serverPublicKey
				}
				reason := challenge.Reason
				if testCase.reason != nil {
					reason = testCase.reason
				}
				approved, err := domain_model.NewSecurityCardSignChallengeV3(
					serverPublicKey, challenge.CallCounter, challenge.Index, reason, challenge.MAC,
				)
				require.NoError(t, err)
				clientSigInHex = signV3ClientApproval(t, testCase.signer, approved)
			}

			err = simulatedHouston.SignSubmitSignedChallenge(model.SignSubmitSignedChallengeJSON{
				ShortDeviceIDInHex: session.SelectResponse().ShortDeviceIDInHex(),
				CardPubKeyInHex:    hex.EncodeToString(response.CardPublicKey),
				MacInHex:           hex.EncodeToString(response.MAC),
				CommitSigInHex:     hex.EncodeToString(response.CommitmentSig),
				ClientSigInHex:     clientSigInHex,
			})

			// Pinned to the client approval: the card's approval is refused
			// with the same code, and it is checked first.
			var houstonError *houston.HoustonResponseError
			require.ErrorAs(t, err, &houstonError)
			require.Equal(t, houston.ErrInvalidSignature, houstonError.ErrorCode)
			require.Contains(t, houstonError.Message, "client approval")
			require.Equal(t, secret, simulatedHouston.PairingSecret())
		})
	}
}

func TestMockCardV3SignChallengeRejectsAResponseForAnotherCard(t *testing.T) {
	// The submit names the card that answered, and the server checks the
	// response against that card's challenge. It issued none for a card it
	// never paired, so a submit naming one is refused.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		challenge := requestV3Challenge(t, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		response, err := session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)

		err = simulatedHouston.SignSubmitSignedChallenge(model.SignSubmitSignedChallengeJSON{
			ShortDeviceIDInHex: anotherCardShortDeviceIDInHex,
			CardPubKeyInHex:    hex.EncodeToString(response.CardPublicKey),
			MacInHex:           hex.EncodeToString(response.MAC),
			CommitSigInHex:     hex.EncodeToString(response.CommitmentSig),
		})

		var houstonError *houston.HoustonResponseError
		require.ErrorAs(t, err, &houstonError)
		require.Equal(t, houston.ErrChallengeExpired, houstonError.ErrorCode)
	})
}

func TestMockCardV3SignChallengeKeysTheResponseMACTheWayItsGenerationDoes(t *testing.T) {
	// The card and the Houston simulator key the response MAC the same way, so the
	// tests that run them together would also pass with both on the wrong
	// key. This one recomputes the MAC from the protocol instead: 3.0 keys it
	// with the secret the request MAC matched, 3.1 with the next_secret the
	// ratchet derives from it, as the spec does.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)
		secret := append([]byte{}, mockCard.ConfirmedSecret()...)

		// A challenge under a key this test holds, so it derives the ratchet
		// on its own rather than asking either side.
		server, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)

		reason := []byte("response mac")
		base := requestV3Challenge(t, simulatedHouston)
		challenge, err := data.MapSecurityCardSignChallengeV3(
			model.SignRequestChallengeResponseJSON{
				ServerPubKeyInHex: hex.EncodeToString(server.PublicKey),
				ReasonInHex:       hex.EncodeToString(reason),
			},
			model.SignChallengePerCardPayloadJSON{
				Index: base.Index,
				MacInHex: hex.EncodeToString(simulatedHouston.ComputeSignChallengeMAC(
					server.PublicKey, base.CallCounter, base.Index, reason,
				)),
				CallCounter: base.CallCounter,
			},
		)
		require.NoError(t, err)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		response, err := session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)

		sharedSecret, err := cryptography.ECDH(server.PrivateKey, response.CardPublicKey)
		require.NoError(t, err)
		nextSecret := nfc.ComputeHMACSHA256(
			secret, append([]byte("challenge-ratchet"), sharedSecret...),
		)

		var responseKey []byte
		switch generation {
		case nfc.MockFirmwareGenerationV3_0:
			responseKey = secret
		case nfc.MockFirmwareGenerationV3_1:
			responseKey = nextSecret
		default:
			t.Fatalf("no response MAC key for generation %s", generation)
		}

		macInput := append([]byte("challenge-response"), server.PublicKey...)
		macInput = append(macInput, response.CardPublicKey...)

		require.Equal(t, nfc.ComputeHMACSHA256(responseKey, macInput), response.MAC)
	})
}

func TestMockCardV3_1SignChallengeRejectsAResponseMACUnderTheMatchedSecret(t *testing.T) {
	// A 3.1 card keys its response MAC with the secret it ratchets to. The
	// Houston simulator has to hold it to that and refuse the 3.0 key, or the MAC
	// would stop proving that the card ratcheted.
	card, mockCard, simulatedHouston := setUpV3Pairing(t, nfc.MockFirmwareGenerationV3_1)
	pairV3CardWithHouston(t, card, simulatedHouston)
	secret := append([]byte{}, mockCard.ConfirmedSecret()...)

	challenge := requestV3Challenge(t, simulatedHouston)

	session, err := card.Connect(t.Context())
	require.NoError(t, err)

	response, err := session.SignChallenge(t.Context(), challenge)
	require.NoError(t, err)

	macInput := append([]byte("challenge-response"), challenge.ServerPublicKey...)
	macInput = append(macInput, response.CardPublicKey...)

	err = simulatedHouston.SignSubmitSignedChallenge(model.SignSubmitSignedChallengeJSON{
		ShortDeviceIDInHex: session.SelectResponse().ShortDeviceIDInHex(),
		CardPubKeyInHex:    hex.EncodeToString(response.CardPublicKey),
		MacInHex:           hex.EncodeToString(nfc.ComputeHMACSHA256(secret, macInput)),
		CommitSigInHex:     hex.EncodeToString(response.CommitmentSig),
	})

	var houstonError *houston.HoustonResponseError
	require.ErrorAs(
		t, err, &houstonError, "a 3.1 response MAC under the matched secret must not verify",
	)
	require.Equal(t, houston.ErrInvalidMac, houstonError.ErrorCode)
}

func TestMockCardV3SignChallengeRejectsAReplayedCounter(t *testing.T) {
	// Replaying a counter is what would let an attacker collect traces of
	// the same computation, so the card refuses before it reaches the HMAC.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		challenge := requestV3Challenge(t, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)
		_, err = session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)

		// The very same challenge again, counter included.
		_, err = session.SignChallenge(t.Context(), challenge)
		require.Error(t, err)

		var cardError *nfc.CardError
		require.ErrorAs(t, err, &cardError)
		assert.Contains(t, cardError.Message, "invalid counter")
	})
}

func TestMockCardV3SignChallengeStreamsALongReason(t *testing.T) {
	// Chunking is a transport decision: the card authenticates and signs the
	// reason's hash, so a reason that arrives in pieces has to produce the
	// same answer as one that fits in a single command.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		reason := bytes.Repeat([]byte("a long reason. "), 60) // 900 bytes
		challenge := buildV3ChallengeWithReason(t, simulatedHouston, reason)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		response, err := session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)

		require.NoError(t, simulatedHouston.VerifySignChallengeResponseMAC(
			challenge.ServerPublicKey,
			response.CardPublicKey,
			response.MAC,
		))

		// The signature covers the whole reason, not just the first chunk.
		reasonHash := sha256.Sum256(reason)
		toBeSigned := append([]byte("card-approval"), challenge.ServerPublicKey...)
		toBeSigned = append(toBeSigned, reasonHash[:]...)

		verified, err := simulatedHouston.VerifySignature(
			mockCard.CommitmentPublicKey(), toBeSigned, response.CommitmentSig,
		)
		require.NoError(t, err)
		require.True(t, verified)
	})
}

func TestMockCardV3StreamedChallengeRejectsAReplayedCounter(t *testing.T) {
	// The single-shot path refuses a replayed counter, and chunking is a
	// transport decision, so a streamed one has to refuse it the same way.
	// Both answer through one shared tail, and this is what keeps them from
	// drifting apart again.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		reason := bytes.Repeat([]byte("a long reason. "), 60) // 900 bytes
		challenge := buildV3ChallengeWithReason(t, simulatedHouston, reason)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)
		_, err = session.SignChallenge(t.Context(), challenge)
		require.NoError(t, err)

		// The very same challenge again, counter included, chunk by chunk.
		_, err = session.SignChallenge(t.Context(), challenge)
		require.Error(t, err)

		var cardError *nfc.CardError
		require.ErrorAs(t, err, &cardError)
		assert.Contains(t, cardError.Message, "invalid counter")
	})
}

func TestMockCardV3SignChallengeReasonLengthBoundary(t *testing.T) {
	// 150 bytes is the last reason a single command carries and 151 the
	// first that has to stream. The card routes on the declared length, so
	// both have to come back with an answer that verifies.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		for _, reasonLength := range []int{150, 151} {
			t.Run(fmt.Sprintf("reason_length_%d", reasonLength), func(t *testing.T) {
				card, _, simulatedHouston := setUpV3Pairing(t, generation)
				pairV3CardWithHouston(t, card, simulatedHouston)

				challenge := buildV3ChallengeWithReason(
					t, simulatedHouston, bytes.Repeat([]byte{0xCC}, reasonLength),
				)

				session, err := card.Connect(t.Context())
				require.NoError(t, err)

				response, err := session.SignChallenge(t.Context(), challenge)
				require.NoError(t, err)

				require.NoError(t, simulatedHouston.VerifySignChallengeResponseMAC(
					challenge.ServerPublicKey,
					response.CardPublicKey,
					response.MAC,
				))
			})
		}
	})
}

func TestMockCardV3SignChallengeSpendsItsFailureBudget(t *testing.T) {
	// The budget is what bounds how many times an attacker can make the card
	// HMAC under one secret, so it has to be spent by attempts that fail too
	// — otherwise cutting the field mid-verification would be free.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		for attempt := range maxFailedChallengesV3 {
			_, err := session.SignChallenge(t.Context(), corruptedV3Challenge(t, simulatedHouston))
			require.Error(t, err, "attempt %d should fail on the MAC", attempt)
			assertV3CardError(t, err, nfc.ErrInternal)
		}

		// Spent. Even a challenge the card could answer is refused now.
		_, err = session.SignChallenge(t.Context(), requestV3Challenge(t, simulatedHouston))
		require.Error(t, err)
		assertV3CardError(t, err, nfc.ErrCardMustPairAgain)
	})
}

func TestMockCardV3SignChallengeClearsTheBudgetOnlyOnAConfirmedRatchet(t *testing.T) {
	// Only a request authenticated with the secret the card proposed proves
	// the server received the response carrying it. A challenge answered
	// under the confirmed secret means the ratchet never advanced, which is
	// exactly the case the budget exists for, so it must not clear it.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, mockCard, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)

		// Drop one response: the card ratchets, the server does not, so from
		// here on its challenges authenticate under the confirmed secret.
		_, err = session.SignChallenge(t.Context(), requestV3Challenge(t, simulatedHouston))
		require.NoError(t, err)
		require.NotEqual(t, mockCard.ConfirmedSecret(), mockCard.ProposedSecret())

		for range maxFailedChallengesV3 - 2 {
			_, err := session.SignChallenge(t.Context(), corruptedV3Challenge(t, simulatedHouston))
			require.Error(t, err)
		}

		// The card answers this one, but under the confirmed secret, so it buys
		// nothing back.
		_, err = session.SignChallenge(t.Context(), requestV3Challenge(t, simulatedHouston))
		require.NoError(t, err)

		_, err = session.SignChallenge(t.Context(), corruptedV3Challenge(t, simulatedHouston))
		require.Error(t, err)

		_, err = session.SignChallenge(t.Context(), requestV3Challenge(t, simulatedHouston))
		assertV3CardError(t, err, nfc.ErrCardMustPairAgain)
	})
}

func TestMockCardV3SignChallengeBudgetSurvivesANewPairing(t *testing.T) {
	// Pairing is the way out, so it has to hand the card a fresh budget.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)
		for range maxFailedChallengesV3 {
			_, err := session.SignChallenge(t.Context(), corruptedV3Challenge(t, simulatedHouston))
			require.Error(t, err)
		}

		pairV3CardWithHouston(t, card, simulatedHouston)

		signV3ChallengeWithHouston(t, card, simulatedHouston)
	})
}

func TestMockCardV3SignChallengeForAnotherPairingSpendsNoAttempt(t *testing.T) {
	// The card refuses a challenge that names a pairing it does not hold
	// before it counts the attempt, so such challenges cannot use up the
	// budget of the pairing it does hold.
	forEachMockFirmwareGenerationV3(t, func(t *testing.T, generation nfc.MockFirmwareGenerationV3) {
		card, _, simulatedHouston := setUpV3Pairing(t, generation)
		pairV3CardWithHouston(t, card, simulatedHouston)

		session, err := card.Connect(t.Context())
		require.NoError(t, err)
		for range maxFailedChallengesV3 {
			challenge := requestV3Challenge(t, simulatedHouston)
			challenge.Index++
			_, err := session.SignChallenge(t.Context(), challenge)
			assertV3CardError(t, err, nfc.ErrSlotNotInitialized)
		}

		signV3ChallengeWithHouston(t, card, simulatedHouston)
	})
}

// spendV3Pairings pairs the card count times against throwaway server keys,
// to move it along its pairing budget without a wallet recording any of
// them.
func spendV3Pairings(t *testing.T, card *nfc.MuunCardV3, count int) {
	t.Helper()

	session, err := card.Connect(t.Context())
	require.NoError(t, err)

	for range count {
		server, err := cryptography.GenerateSecp256r1PKeyPair()
		require.NoError(t, err)

		_, err = session.Pair(t.Context(), server.PublicKey)
		require.NoError(t, err)
	}
}

func requestV3Challenge(
	t *testing.T,
	simulatedHouston *simulator.SimulatedHoustonService,
) *domain_model.SecurityCardSignChallengeV3 {
	t.Helper()

	response, err := simulatedHouston.SignRequestChallenge(model.SignRequestChallengeJSON{})
	require.NoError(t, err)
	require.Len(t, response.PerCardPayloads, 1)

	challenge, err := data.MapSecurityCardSignChallengeV3(
		response, response.PerCardPayloads[0],
	)
	require.NoError(t, err)

	return challenge
}

// signV3ChallengeWithHouston runs one full challenge and returns what each
// side put into it, so a test can check the card's answer against the request
// it was made for.
func signV3ChallengeWithHouston(
	t *testing.T,
	card *nfc.MuunCardV3,
	simulatedHouston *simulator.SimulatedHoustonService,
) (*domain_model.SecurityCardSignChallengeV3, *nfc.ChallengeResponseV3) {
	t.Helper()

	challenge := requestV3Challenge(t, simulatedHouston)

	session, err := card.Connect(t.Context())
	require.NoError(t, err)

	response, err := session.SignChallenge(t.Context(), challenge)
	require.NoError(t, err)

	err = simulatedHouston.SignSubmitSignedChallenge(model.SignSubmitSignedChallengeJSON{
		ShortDeviceIDInHex: session.SelectResponse().ShortDeviceIDInHex(),
		CardPubKeyInHex:    hex.EncodeToString(response.CardPublicKey),
		MacInHex:           hex.EncodeToString(response.MAC),
		CommitSigInHex:     hex.EncodeToString(response.CommitmentSig),
		ClientSigInHex:     signV3ClientApproval(t, v3TestUserKey(t), challenge),
	})
	require.NoError(t, err)

	return challenge, response
}

// buildV3ChallengeWithReason issues a challenge carrying a given reason.
// SignRequestChallenge always sends an empty one, since Houston does not
// generate the text yet, so a test that needs one builds it here with the
// server's own MAC.
func buildV3ChallengeWithReason(
	t *testing.T,
	simulatedHouston *simulator.SimulatedHoustonService,
	reason []byte,
) *domain_model.SecurityCardSignChallengeV3 {
	t.Helper()

	base := requestV3Challenge(t, simulatedHouston)

	mac := simulatedHouston.ComputeSignChallengeMAC(
		base.ServerPublicKey,
		base.CallCounter,
		base.Index,
		reason,
	)

	challenge, err := data.MapSecurityCardSignChallengeV3(
		model.SignRequestChallengeResponseJSON{
			ServerPubKeyInHex: hex.EncodeToString(base.ServerPublicKey),
			ReasonInHex:       hex.EncodeToString(reason),
		},
		model.SignChallengePerCardPayloadJSON{
			Index:       base.Index,
			MacInHex:    hex.EncodeToString(mac),
			CallCounter: base.CallCounter,
		},
	)
	require.NoError(t, err)

	return challenge
}

// maxFailedChallengesV3 mirrors the firmware's MAX_FAILURES, the consecutive
// challenges a card answers without one confirming the ratchet.
const maxFailedChallengesV3 = 32

// corruptedV3Challenge issues a real challenge and then breaks its MAC, so the
// card rejects it after spending an attempt.
func corruptedV3Challenge(
	t *testing.T,
	simulatedHouston *simulator.SimulatedHoustonService,
) *domain_model.SecurityCardSignChallengeV3 {
	t.Helper()

	challenge := requestV3Challenge(t, simulatedHouston)
	challenge.MAC[0] ^= 0xFF

	return challenge
}

func assertV3CardError(t *testing.T, err error, expected nfc.CardErrorCode) {
	t.Helper()

	var cardError *nfc.CardError
	require.ErrorAs(t, err, &cardError)
	require.Equal(t, expected, cardError.Code)
}
