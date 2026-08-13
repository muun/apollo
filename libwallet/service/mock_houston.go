package service

import (
	"crypto/ecdh"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"math/big"
	"reflect"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/cryptography"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
)

type RandomPrivateKeyMetadata struct {
	privateKey *ecdh.PrivateKey
	timeStamp  time.Time
}

type MockHoustonService struct {
	keyValueStorage              *storage.KeyValueStorage
	lastRandomPrivateKeyMetadata *RandomPrivateKeyMetadata
	secretCardBytes              [32]byte
	securityCardUsageCount       uint16
	replayCounter                uint16
	pairingSlot                  uint16
}

var _ HoustonService = (*MockHoustonService)(nil)

const challengeTimeoutInSeconds = 90

const (
	ErrChallengeExpired = 2090
	ErrInvalidSignature = 2091
	ErrInvalidMac       = 2092
	ErrUnknown          = 100_000
)

const (
	StatusClientFailure = 400
	StatusServerFailure = 500
)

func NewMockHoustonService(storage *storage.KeyValueStorage) *MockHoustonService {
	return &MockHoustonService{keyValueStorage: storage}
}

func (m *MockHoustonService) HealthCheck() error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeKeySetupStart(
	req model.ChallengeSetupJson,
) (model.SetupChallengeResponseJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeSetupFinishWithVerifiableMuunKey(
	req model.ChallengeSetupVerifyJson,
) (model.VerifiableMuunKeyJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) VerifiableMuunKey() (model.VerifiableMuunKeyJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) CreateFirstSession(
	createSessionJson model.CreateFirstSessionJson,
) (model.CreateFirstSessionOkJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) FetchFeeWindow() (model.FeeWindowJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) PairRequestChallenge() (model.PairRequestChallengeResponseJSON, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	// Persists the fresh challenge so the upcoming submit loads it.
	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	randomPubKey := randomPrivateKey.PublicKey().Bytes()

	return model.PairRequestChallengeResponseJSON{
		ServerPubKeyInHex: hex.EncodeToString(randomPubKey),
	}, nil
}

// Deprecated: V2 firmware only, will be removed once V3 is fully released.
func (m *MockHoustonService) RegisterSecurityCard(
	req model.RegisterSecurityCardJson,
) (model.RegisterSecurityCardOkJson, error) {
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		houstonError := &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	cardPublicKeyBytes, err := hex.DecodeString(req.CardPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	clientPublicKeyBytes, err := hex.DecodeString(req.ClientPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding client pub key", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	sharedPoint, err := cryptography.ECDH(
		m.lastRandomPrivateKeyMetadata.privateKey.Bytes(),
		cardPublicKeyBytes,
	)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("ecdh error", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Compute secret_card = sha256(shared_point)
	secretCard := sha256.Sum256(sharedPoint)
	macSecretCard := secretCard[:16]
	encSecretCard := secretCard[16:]

	slog.Debug("macSecretCard", slog.String("secret", hex.EncodeToString(macSecretCard)))
	slog.Debug("encSecretCard", slog.String("secret", hex.EncodeToString(encSecretCard)))

	metadataBytes, err := SecurityCardMetadataToBytes(req.Metadata)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card metadata", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding mac", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Verify MAC: mac = hmac(mac_secret_card, C || P || index || metadata || pub_client)
	err = verifyPairingMAC(
		cardPublicKeyBytes,
		nfc.IntTo2Bytes(req.PairingSlot),
		metadataBytes,
		m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes(),
		clientPublicKeyBytes,
		macSecretCard,
		receivedMacBytes,
	)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	globalSignCardBytes, err := hex.DecodeString(req.GlobalSignCardInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding global sign card", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	globalPublicKeyBytes, err := hex.DecodeString(req.Metadata.GlobalPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding global public card", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Verify signed MAC with global public key
	isValidated, err := m.verifySignature(
		globalPublicKeyBytes,
		receivedMacBytes,
		globalSignCardBytes,
	)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error with mac sig verification", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	if !isValidated {
		houstonError := &HoustonResponseError{
			DeveloperMessage: "signature could not be verified. Signed content was altered or signed with invalid/incorrect key",
			ErrorCode:        ErrInvalidSignature,
			Message:          "invalid signature",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Store card and secret data
	m.securityCardUsageCount = req.Metadata.UsageCount
	m.secretCardBytes = secretCard
	m.pairingSlot = req.PairingSlot

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// TODO: Check if something should change on metadata returned
	enrichedMetadata := req.Metadata

	return model.RegisterSecurityCardOkJson{
		Metadata:          enrichedMetadata,
		IsKnownProvider:   true,
		IsCardAlreadyUsed: false,
	}, nil
}

// PairSubmitSignedChallenge completes the V3 pairing flow.
func (m *MockHoustonService) PairSubmitSignedChallenge(
	req model.PairSubmitSignedChallengeJSON,
) (model.PairSubmitSignedChallengeResponseJSON, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	if m.lastRandomPrivateKeyMetadata == nil {
		return model.PairSubmitSignedChallengeResponseJSON{}, &HoustonResponseError{
			DeveloperMessage: "no pending pair challenge",
			ErrorCode:        ErrChallengeExpired,
			Message:          "no pending pair challenge",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// Validates the challenge timeout (90s since PairRequestChallenge).
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return model.PairSubmitSignedChallengeResponseJSON{}, &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	metadataBytes, err := hex.DecodeString(req.MetadataInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding metadata", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	// Parses the metadata; ParseMetadataV3 also validates that
	// attestationPub lies on secp256r1.
	metadata, err := nfc.ParseMetadataV3(metadataBytes)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("invalid metadata", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	// TODO (Houston): validate action_pub lies on secp256r1.

	// Validates P lies on secp256r1.
	cardPubKeyBytes, err := hex.DecodeString(req.CardPubKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}
	if err := cryptography.ValidateSecp256r1PublicKey(cardPubKeyBytes); err != nil {
		houstonError := mapToInternalServerHoustonError("invalid card public key", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	// Computes DH1 = c·attestationPubKey and DH2 = muunPriv·P, then
	// derives secret_card = HMAC("pairing-secret", DH1||DH2).
	serverPrivKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.Bytes()
	dh1, err := cryptography.ECDH(serverPrivKeyBytes, metadata.AttestationPub[:])
	if err != nil {
		houstonError := mapToInternalServerHoustonError("DH1 error", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}
	dh2, err := cryptography.ECDH(muunPrivDevBytes, cardPubKeyBytes)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("DH2 error", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	ikm := make([]byte, 0, len(dh1)+len(dh2))
	ikm = append(ikm, dh1...)
	ikm = append(ikm, dh2...)
	secretCard := nfc.ComputeHMACSHA256([]byte("pairing-secret"), ikm)

	// Verifies MAC = HMAC(secret_card, "pairing-response"||C||P||index||metadata).
	// Constant-time compare via hmac.Equal.
	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding mac", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	serverPubKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	err = verifyPairingMACV3(
		secretCard,
		serverPubKeyBytes,
		cardPubKeyBytes,
		req.Index,
		metadataBytes,
		receivedMacBytes,
	)
	if err != nil {
		return model.PairSubmitSignedChallengeResponseJSON{}, &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// TODO Houston: verify the attestation certificate chain. The metadata
	// carries providerPubKey + providerSig where providerSig is an ECDSA
	// signature over attestationPubKey. Houston should verify that
	// providerPubKey belongs to a trusted provider allowlist and that
	// providerSig validates attestationPubKey under it. On failure return
	// SECURITY_CARD_INVALID_SIGNATURE; the response's
	// IsKnownProvider flag mirrors whether the chain validated. The mock
	// currently hardcodes IsKnownProvider=true because it has no allowlist.

	// TODO Houston: reject if a card with this attestationPubKey is already
	// paired to this wallet. The attestationPubKey is the card's permanent
	// identity, so collisions imply double-pairing. On failure return
	// SECURITY_CARD_ALREADY_PAIRED_TO_THIS_WALLET. The mock cannot
	// enforce this without a persisted card registry, which is out of
	// scope here.

	// replayCounter starts at 0 for this slot — it tracks per-slot sign
	// challenge usage for replay protection.
	m.replayCounter = 0
	copy(m.secretCardBytes[:], secretCard)
	m.pairingSlot = req.Index

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	metadataJSON, err := mapSecurityCardV3MetadataJSON(metadata)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error mapping metadata", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	return model.PairSubmitSignedChallengeResponseJSON{
		SecurityCard: model.PairedSecurityCardJSON{
			ID:       1,
			Metadata: *metadataJSON,
			PairedAt: time.Now().UTC().Format(time.RFC3339),
		},
		IsKnownProvider:   true,
		IsCardAlreadyUsed: false,
	}, nil
}

// SignRequestChallenge issues a V3 sign challenge to approve a sensitive
// action (e.g. a new on-chain operation). The card MAC-verifies the
// challenge and emits a response after the user taps to approve.
func (m *MockHoustonService) SignRequestChallenge(
	_ model.SignRequestChallengeJSON,
) (model.SignRequestChallengeResponseJSON, error) {

	// Loads the paired card state (secret_card, counter).
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	// Generates ephemeral (c, C), increments replayCounter.
	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	// NOTE (Houston): issuing a new challenge should expire any open
	// challenge already outstanding for this wallet. The mock holds a
	// single in-flight challenge, so overwriting lastRandomPrivateKeyMetadata
	// implicitly drops the previous one; real Houston must expire them
	// explicitly so stale challenges can't be replayed.
	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	m.replayCounter++
	serverPubKeyBytes := randomPrivateKey.PublicKey().Bytes()

	// Empty reason: the card displays this text on its screen during
	// approval, so only screen-capable cards need it populated. The
	// mock doesn't persist capabilities yet, so it defaults to the
	// non-screen behavior (empty).
	// TODO (Houston): generate the reason text for screen-capable cards.
	var reasonBytes []byte

	// index is the pairing slot persisted at pair time.
	// V3 firmware is single-slot today (so it is 0).
	macBytes := computeSignChallengeMACV3(
		m.secretCardBytes[:],
		serverPubKeyBytes,
		m.replayCounter,
		m.pairingSlot,
		reasonBytes,
	)

	// Persists the advanced counter and new ephemeral so the upcoming
	// submit can verify the card's response.
	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	return model.SignRequestChallengeResponseJSON{
		ServerPubKeyInHex: hex.EncodeToString(serverPubKeyBytes),
		ReasonInHex:       hex.EncodeToString(reasonBytes),
		PerCardPayloads: []model.SignChallengePerCardPayloadJSON{
			{
				// TODO multi-pairing: leave empty until firmware + V3
				// client + Houston track attestationPubKey end-to-end.
				AttestationPubKeyInHex: "",
				Index:                  m.pairingSlot,
				MacInHex:               hex.EncodeToString(macBytes),
				ReplayCounter:          m.replayCounter,
			},
		},
	}, nil
}

// SignSubmitSignedChallenge completes the V3 sign flow. The client
// forwards the card's response (P, MAC) from the sign challenge.
func (m *MockHoustonService) SignSubmitSignedChallenge(
	req model.SignSubmitSignedChallengeJSON,
) error {
	// Loads the in-flight ephemeral c and the current secret_card.
	err := m.loadCardData()
	if err != nil {
		return mapToInternalServerHoustonError("error loading houston data", err)
	}

	if m.lastRandomPrivateKeyMetadata == nil {
		return &HoustonResponseError{
			DeveloperMessage: "no pending sign challenge",
			ErrorCode:        ErrChallengeExpired,
			Message:          "no pending sign challenge",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// Validates the challenge timeout (90s since SignRequestChallenge).
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// Decodes and validates P on secp256r1.
	cardPubKeyBytes, err := hex.DecodeString(req.CardPubKeyInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding card pub key", err)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(cardPubKeyBytes); err != nil {
		return mapToInternalServerHoustonError("invalid card public key", err)
	}

	// Verifies MAC = HMAC(secret_card, "challenge-response"||C||P).
	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding mac", err)
	}

	serverPubKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	err = verifySignChallengeResponseMACV3(
		m.secretCardBytes[:],
		serverPubKeyBytes,
		cardPubKeyBytes,
		receivedMacBytes,
	)
	if err != nil {
		return &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// TODO (Houston): verify action_sig — the card's signature over the
	// approved action — to prove the tap authorized this specific action.

	// Ratchet secret_card := HMAC(secret_card, "challenge-ratchet"||DH)
	// where DH = ECDH(c, P). The card computes the same ratchet on its
	// side from (p, C), so the next sign challenge uses a fresh secret
	// on both ends.
	dh, err := cryptography.ECDH(
		m.lastRandomPrivateKeyMetadata.privateKey.Bytes(),
		cardPubKeyBytes,
	)
	if err != nil {
		return mapToInternalServerHoustonError("ECDH error", err)
	}

	ratchetIKM := append([]byte("challenge-ratchet"), dh...)
	newSecretCard := nfc.ComputeHMACSHA256(m.secretCardBytes[:], ratchetIKM)
	copy(m.secretCardBytes[:], newSecretCard)

	// Persists the new secret_card.
	err = m.persistCardData()
	if err != nil {
		return mapToInternalServerHoustonError("error persisting houston data", err)
	}

	return nil
}

// Deprecated: V2 firmware only, will be removed once V3 is fully released.
func (m *MockHoustonService) ChallengeSecurityCardSign(
	req model.ChallengeSecurityCardSignJson,
) (model.ChallengeSecurityCardSignResponseJson, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	m.securityCardUsageCount += 1

	randomPublicKey := randomPrivateKey.PublicKey().Bytes()

	reasonBytes, err := hex.DecodeString(req.ReasonInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding reason", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	challengeMac := nfc.MakeChallengeSignMac(
		m.secretCardBytes[:16],
		randomPublicKey,
		reasonBytes,
		m.securityCardUsageCount,
		m.pairingSlot,
	)

	return model.ChallengeSecurityCardSignResponseJson{
		ServerPublicKeyInHex: hex.EncodeToString(randomPublicKey),
		CardUsageCount:       m.securityCardUsageCount,
		MacInHex:             hex.EncodeToString(challengeMac),
		PairingSlot:          m.pairingSlot,
	}, nil
}

// Deprecated: V2 firmware only, will be removed once V3 is fully released.
func (m *MockHoustonService) SolveSecurityCardChallenge(
	req model.SolveSecurityCardChallengeJson,
) error {
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	serverPublicKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	cardPublicKeyBytes, err := hex.DecodeString(req.PublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return houstonError
	}

	receivedMac, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding received mac", err)
		return houstonError
	}

	err = m.verifySolveChallengeMac(receivedMac, serverPublicKeyBytes, cardPublicKeyBytes)
	if err != nil {
		return &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// Calculate and store new secret card
	sharedPoint, err := cryptography.ECDH(
		m.lastRandomPrivateKeyMetadata.privateKey.Bytes(),
		cardPublicKeyBytes,
	)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("ecdh error", err)
		return houstonError
	}

	// Update secret for forward secrecy: new_secret = HMAC(currentSecretCardBytes, sharedPoint)
	newSecretCardBytes := nfc.ComputeHMACSHA256(m.secretCardBytes[:], sharedPoint)
	copy(m.secretCardBytes[:], newSecretCardBytes)

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return houstonError
	}

	return nil
}

func (m *MockHoustonService) FetchSecurityCardsMarketplace() (model.SecurityCardsMarketplaceJson, error) {
	argentina := model.CountryInfoJson{Code: "AR", Name: "Argentina", Flag: "🇦🇷"}
	uruguay := model.CountryInfoJson{Code: "UY", Name: "Uruguay", Flag: "🇺🇾"}
	brazil := model.CountryInfoJson{Code: "BR", Name: "Brazil", Flag: "🇧🇷"}

	return model.SecurityCardsMarketplaceJson{
		Providers: []model.SecurityCardsProviderJson{
			{
				Id:          "constellations",
				Name:        "Constellations",
				Description: "Constellations are officially recognized patterns of stars in the night sky that form recognizable shapes, figures, or outlines.",
				SiteUrl:     "https://en.wikipedia.org/wiki/Constellation",
				LightTheme: model.ProviderThemeJson{
					PrimaryColor: "#B19B6A",
					SurfaceColor: "#0DB19B6A",
				},
				DarkTheme: model.ProviderThemeJson{
					PrimaryColor: "#B19B6A",
					SurfaceColor: "#0DB19B6A",
				},
				SecurityCards: []model.SecurityCardJson{
					{
						Id:       "constellations_scorpius",
						AssetUrl: "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=SCORPIUS",
						SpecId:   "constellations_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "EUR", Amount: "10000"},
					},
					{
						Id:       "constellations_gemini",
						AssetUrl: "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=GEMINI",
						SpecId:   "constellations_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "EUR", Amount: "20000"},
					},
					{
						Id:       "constellations_sagitarius",
						AssetUrl: "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=SAGITARIUS",
						SpecId:   "constellations_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "EUR", Amount: "30000"},
					},
					{
						Id:       "constellations_virgo",
						AssetUrl: "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=VIRGO",
						SpecId:   "constellations_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "EUR", Amount: "30000"},
					},
				},
				EstimatedShippingPrices: []model.ShippingPriceInfoJson{
					{
						Price:     model.PriceInfoJson{CurrencyCode: "EUR", Amount: "1500"},
						Countries: []model.CountryInfoJson{argentina, uruguay},
					},
					{
						Price:     model.PriceInfoJson{CurrencyCode: "EUR", Amount: "3000"},
						Countries: []model.CountryInfoJson{brazil},
					},
				},
			},
			{
				Id:          "numbers",
				Name:        "Numbers",
				Description: "Numbers are mathematical objects used for counting, measuring, and labeling, with primary types including natural numbers (1, 2, 3...), whole numbers, and integers",
				SiteUrl:     "https://en.wikipedia.org/wiki/Number",
				LightTheme: model.ProviderThemeJson{
					PrimaryColor: "#D9DBDD",
					SurfaceColor: "#0DD9DBDD",
				},
				DarkTheme: model.ProviderThemeJson{
					PrimaryColor: "#D9DBDD",
					SurfaceColor: "#0DD9DBDD",
				},
				SecurityCards: []model.SecurityCardJson{
					{
						Id:       "numbers_1",
						AssetUrl: "https://placehold.co/2594x1632/8B1A1A/4A0D0D/png?text=1",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "USD", Amount: "10000"},
					},
					{
						Id:       "numbers_2",
						AssetUrl: "https://placehold.co/2594x1632/C9A227/7A5C10/png?text=2",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "20000"},
					},
					{
						Id:       "numbers_3",
						AssetUrl: "https://placehold.co/2594x1632/1A4E8C/0C2E5E/png?text=3",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "30000"},
					},
					{
						Id:       "numbers_4",
						AssetUrl: "https://placehold.co/2594x1632/C45A1A/6E2E08/png?text=4",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "40000"},
					},
					{
						Id:       "numbers_5",
						AssetUrl: "https://placehold.co/2594x1632/1A6E3A/0B3D1F/png?text=5",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "50000"},
					},
					{
						Id:       "numbers_6",
						AssetUrl: "https://placehold.co/2594x1632/5E2A7A/2E1240/png?text=6",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "60000"},
					},
					{
						Id:       "numbers_7",
						AssetUrl: "https://placehold.co/2594x1632/0E5E6E/04323E/png?text=7",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "70000"},
					},
					{
						Id:       "numbers_8",
						AssetUrl: "https://placehold.co/2594x1632/19A0B0/0A5E6A/png?text=8",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "80000"},
					},
					{
						Id:       "numbers_9",
						AssetUrl: "https://placehold.co/2594x1632/B02A8E/5C1149/png?text=9",
						Tag:      "OUT_OF_STOCK",
						SpecId:   "numbers_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "ARS", Amount: "90000"},
					},
				},
				EstimatedShippingPrices: []model.ShippingPriceInfoJson{
					{
						Price:     model.PriceInfoJson{CurrencyCode: "USD", Amount: "1000"},
						Countries: []model.CountryInfoJson{argentina, uruguay},
					},
					{
						Price:     model.PriceInfoJson{CurrencyCode: "USD", Amount: "2000"},
						Countries: []model.CountryInfoJson{brazil},
					},
				},
			},
			{
				Id:          "planets",
				Name:        "Planets",
				Description: "There are eight officially recognized planets in our solar system, orbiting the Sun in this order: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune.",
				SiteUrl:     "https://en.wikipedia.org/wiki/Planet",
				LightTheme: model.ProviderThemeJson{
					PrimaryColor: "#158E5A",
					SurfaceColor: "#0D158E5A",
				},
				DarkTheme: model.ProviderThemeJson{
					PrimaryColor: "#158E5A",
					SurfaceColor: "#0D158E5A",
				},
				SecurityCards: []model.SecurityCardJson{
					{
						Id:       "planets_earth",
						AssetUrl: "https://placehold.co/2594x1632/081448/3B5D38/png?text=EARTH",
						Tag:      "METAL",
						SpecId:   "planets_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "USD", Amount: "10000"},
					},
					{
						Id:       "planets_mars",
						AssetUrl: "https://placehold.co/2594x1632/081448/C1440E/png?text=MARS",
						Tag:      "METAL",
						SpecId:   "planets_spec",
						CardCost: model.PriceInfoJson{CurrencyCode: "USD", Amount: "20000"},
					},
				},
				EstimatedShippingPrices: []model.ShippingPriceInfoJson{
					{
						Price:     model.PriceInfoJson{CurrencyCode: "USD", Amount: "1000"},
						Countries: []model.CountryInfoJson{argentina, uruguay},
					},
					{
						Price:     model.PriceInfoJson{CurrencyCode: "USD", Amount: "2000"},
						Countries: []model.CountryInfoJson{brazil},
					},
				},
			},
		},
		Specs: []model.SecurityCardSpecJson{
			{
				SpecId: "constellations_spec",
				Items: map[string][]model.SecurityCardSpecItemJson{
					"primary": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Plastic"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Sky"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Already there"},
					},
					"specifications": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Plastic"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Thickness", Value: "0.8mm"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Weight", Value: "5g"},
					},
					"security": {
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Secure Element",
							Value:          "EAL 5+",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Firmware",
							Value:          "Designed by Muun",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Packaging", Value: "Tamper resistant"},
					},
					"delivery": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Shipped by", Value: "Sky"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Sky"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Already there"},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Shipping data",
							Value:          "Under GDPR",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
					},
				},
			},
			{
				SpecId: "numbers_spec",
				Items: map[string][]model.SecurityCardSpecItemJson{
					"primary": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Plastic"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Math"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Already here"},
					},
					"specifications": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Plastic"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Thickness", Value: "0.8mm"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Weight", Value: "5g"},
					},
					"security": {
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Secure Element",
							Value:          "EAL 5+",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Firmware",
							Value:          "Designed by Muun",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Packaging", Value: "Tamper resistant"},
					},
					"delivery": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Shipped by", Value: "Math"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Math"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Already here"},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Shipping data",
							Value:          "Under GDPR",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
					},
				},
			},
			{
				SpecId: "planets_spec",
				Items: map[string][]model.SecurityCardSpecItemJson{
					"primary": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Metal"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Space"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Now"},
					},
					"specifications": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Material", Value: "Metal"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Thickness", Value: "1.2mm"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Weight", Value: "10g"},
					},
					"security": {
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Secure Element",
							Value:          "EAL 6+",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Firmware",
							Value:          "Designed by Muun",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Packaging", Value: "Tamper resistant"},
					},
					"delivery": {
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Shipped by", Value: "BigBang"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "From", Value: "Space"},
						{IconUrl: "https://placehold.co/16x16/FF0000/000000/png?text=ic", Label: "Arrives in", Value: "Now"},
						{
							IconUrl:        "https://placehold.co/16x16/FF0000/000000/png?text=ic",
							Label:          "Shipping data",
							Value:          "Under GDPR",
							AdditionalData: "Lorem Ipsum Lorem Ipsum Lorem Ipsum",
						},
					},
				},
			},
		},
	}, nil
}

func (m *MockHoustonService) verifySolveChallengeMac(
	receivedMac,
	serverPublicKeyBytes,
	cardPublicKeyBytes []byte,
) error {
	// Construct MAC input with: C || P
	macInput := make([]byte, 0, 130)
	macInput = append(macInput, serverPublicKeyBytes...) // C (server ephemeral pub key 65 bytes)
	macInput = append(macInput, cardPublicKeyBytes...)   // P (card ephemeral pub key 65 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[:16])
	expectedMAC := nfc.ComputeHMACSHA256(m.secretCardBytes[:16], macInput)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("MAC mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
	}
	return nil
}

func verifyPairingMAC(cardPublicKey,
	pairingSlot,
	metadata,
	serverRandomPubKey,
	clientPubKey,
	macSecretCard,
	receivedMac []byte,
) error {
	// Construct MAC input with: C || P || index || metadata || pub_client
	macInput := make([]byte, 0, 272)
	macInput = append(macInput, serverRandomPubKey...) // C (server random key, 65 bytes)
	macInput = append(macInput, cardPublicKey...)      // P (card public key, 65 bytes)
	macInput = append(macInput, pairingSlot...)        // index (2 bytes)
	macInput = append(macInput, metadata...)           // metadata (75 bytes)
	macInput = append(macInput, clientPubKey...)       // pub_client (65 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[16:])
	expectedMAC := nfc.ComputeHMACSHA256(macSecretCard, macInput)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("mac mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
	}

	return nil
}

// verifyPairingMACV3 verifies the V3 pair-response MAC:
//
//	MAC = HMAC(secret_card, "pairing-response" || C || P || index || metadata)
//
// metadataBytes is the raw wire input the card signed. Re-serializing
// the struct fields would risk drift and break the MAC.
//
// Uses hmac.Equal for constant-time comparison.
func verifyPairingMACV3(
	secretCard,
	serverPubKey,
	cardPubKey []byte,
	index uint16,
	metadataBytes,
	receivedMac []byte,
) error {
	tag := []byte("pairing-response")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+len(cardPubKey)+2+len(metadataBytes),
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = append(macInput, cardPubKey...)
	macInput = append(macInput, nfc.IntTo2Bytes(index)...)
	macInput = append(macInput, metadataBytes...)

	expectedMAC := nfc.ComputeHMACSHA256(secretCard, macInput)
	if !hmac.Equal(receivedMac, expectedMAC) {
		return errors.Errorf("v3 pairing MAC mismatch")
	}
	return nil
}

// maxRawReasonSizeV3 is the reason-length boundary for how the MAC
// commits to the reason. It equals what fits raw in a single short APDU
// alongside the fixed request fields
// (255 − C(65) − counter(2) − index(2) − has_more(1) − mac(32) = 153).
// At or below it the MAC commits to the raw reason; above it the MAC
// commits to SHA256(reason). Houston mirrors it so its MAC matches the card's.
const maxRawReasonSizeV3 = 153

// computeSignChallengeMACV3 computes the V3 sign-request MAC:
//
//	MAC = HMAC(secret_card, "challenge-request" || C || counter || index || reasonForMAC)
//
// C is the server's ephemeral pub key, counter is the per-slot monotonic
// count_card, index is the pairing slot, and reason is the human-readable
// action text that the card would display in screen-capable variants.

// reasonForMAC is the raw reason when it fits a short APDU, else
// SHA256(reason), matching how the card computes the MAC.
func computeSignChallengeMACV3(
	secretCard,
	serverPubKey []byte,
	counter,
	index uint16,
	reason []byte,
) []byte {
	// The card commits to the raw reason at or below the short-APDU
	// length, and to SHA256(reason) above it.
	reasonForMAC := reason
	if len(reason) > maxRawReasonSizeV3 {
		h := sha256.Sum256(reason)
		reasonForMAC = h[:]
	}

	tag := []byte("challenge-request")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+2+2+len(reasonForMAC),
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = append(macInput, nfc.IntTo2Bytes(counter)...)
	macInput = append(macInput, nfc.IntTo2Bytes(index)...)
	macInput = append(macInput, reasonForMAC...)
	return nfc.ComputeHMACSHA256(secretCard, macInput)
}

// verifySignChallengeResponseMACV3 verifies the V3 sign-response MAC:
//
//	MAC = HMAC(secret_card, "challenge-response" || C || P)
//
// C is the server's ephemeral pub key from the sign-request and P is
// the card's ephemeral pub key from its response.
// No counter binding on the response MAC — that's a deliberate spec choice;
// the request MAC already commits to the counter.
func verifySignChallengeResponseMACV3(
	secretCard,
	serverPubKey,
	cardPubKey,
	receivedMac []byte,
) error {
	tag := []byte("challenge-response")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+len(cardPubKey),
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = append(macInput, cardPubKey...)

	expectedMAC := nfc.ComputeHMACSHA256(secretCard, macInput)
	if !hmac.Equal(receivedMac, expectedMAC) {
		return errors.Errorf("v3 sign challenge response MAC mismatch")
	}
	return nil
}

func SecurityCardMetadataToBytes(m model.SecurityCardMetadataJson) ([]byte, error) {
	handleError := func(err error) error {
		return fmt.Errorf("error decoding metadata: %w", err)
	}
	const MetadataSize = 75
	buf := make([]byte, 0, MetadataSize) // 75 bytes

	// Global public key (65 bytes)
	globalPublicKeyBytes, err := hex.DecodeString(m.GlobalPublicKeyInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, globalPublicKeyBytes...)

	// Card vendor (2 bytes)
	cardVendorBytes, err := hex.DecodeString(m.CardVendorInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, cardVendorBytes...)

	// Card model (2 bytes)
	cardModelBytes, err := hex.DecodeString(m.CardModelInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, cardModelBytes...)

	// Firmware version (2 bytes)
	buf = append(buf, nfc.IntTo2Bytes(m.FirmwareVersion)...)

	// Usage count (2 bytes, big-endian)
	buf = append(buf, nfc.IntTo2Bytes(m.UsageCount)...)

	// Language code (2 bytes)
	languageCodeBytes, err := hex.DecodeString(m.LanguageCodeInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, languageCodeBytes...)

	return buf, nil
}

// verifySignature verifies a signature from a muuncard.
func (m *MockHoustonService) verifySignature(
	publicKeyBytes, messageBytes, signedMessageBytes []byte,
) (bool, error) {

	// verify expected public key
	if len(publicKeyBytes) != 65 || publicKeyBytes[0] != 0x04 {
		return false, nil
	}

	ecdhPub, err := ecdh.P256().NewPublicKey(publicKeyBytes)
	if err != nil {
		return false, nil
	}

	pub, err := ecdhToECDSAPublicKey(ecdhPub)
	if err != nil {
		return false, nil
	}

	h := sha256.Sum256(messageBytes)

	// Verify the signature
	return ecdsa.VerifyASN1(pub, h[:], signedMessageBytes), nil
}

// ecdhToECDSAPublicKey converts an *ecdh.PublicKey into an *ecdsa.PublicKey.
// Hacky workaround to avoid deprecated elliptic.Unmarshal() and ignoring lint check.
// Only works for P-256 NIST curve, which is what we are using.
// Once we upgrade to Go 1.25 we could use proper support to transform ecdh.PublicKey to
// ecdsa.PublicKey. for now this is what we got.
func ecdhToECDSAPublicKey(key *ecdh.PublicKey) (*ecdsa.PublicKey, error) {
	if key.Curve() != ecdh.P256() {
		return nil, errors.New("public key curve not supported. We work with P256")
	}

	rawKey := key.Bytes()
	return &ecdsa.PublicKey{
		Curve: elliptic.P256(),
		// For
		X: big.NewInt(0).SetBytes(rawKey[1:33]),
		Y: big.NewInt(0).SetBytes(rawKey[33:]),
	}, nil
}

func mapToInternalServerHoustonError(message string, errCause error) *HoustonResponseError {
	return &HoustonResponseError{
		DeveloperMessage: errCause.Error(),
		ErrorCode:        ErrUnknown,
		Message:          message,
		RequestId:        0,
		Status:           StatusServerFailure,
	}
}

func (m *MockHoustonService) persistCardData() error {
	var items = make(map[string]any)

	if m.lastRandomPrivateKeyMetadata != nil {
		privKeyInHex := hex.EncodeToString(m.lastRandomPrivateKeyMetadata.privateKey.Bytes())
		items[storage.KeyLastRandomPrivKeyInHex] = privKeyInHex

		items[storage.KeyTimeSinceLastChallengeUnixMillis] = m.lastRandomPrivateKeyMetadata.timeStamp.Unix()
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	items[storage.KeySecurityCardUsageCount] = int32(m.securityCardUsageCount)
	items[storage.KeySecurityCardReplayCounter] = int32(m.replayCounter)
	items[storage.KeySecurityCardPairingSlot] = int32(m.pairingSlot)

	secretCardInHex := hex.EncodeToString(m.secretCardBytes[:])
	items[storage.KeySecretCardBytesInHex] = secretCardInHex

	slog.Debug("mockHouston - stored data", "data", items)

	err := m.keyValueStorage.SaveBatch(items)
	if err != nil {
		return fmt.Errorf("error saving mock houston data: %w", err)
	}

	return nil
}

func (m *MockHoustonService) loadCardData() error {
	var keys = []string{
		storage.KeyLastRandomPrivKeyInHex,
		storage.KeySecurityCardUsageCount,
		storage.KeySecurityCardReplayCounter,
		storage.KeySecurityCardPairingSlot,
		storage.KeySecretCardBytesInHex,
		storage.KeyTimeSinceLastChallengeUnixMillis,
	}

	keyValues, err := m.keyValueStorage.GetBatch(keys)
	if err != nil {
		return fmt.Errorf("error loading mock houston data: %w", err)
	}

	slog.Debug("mock houston - loaded data", "data", keyValues)

	if keyValues[storage.KeyLastRandomPrivKeyInHex] != nil {
		privKeyBytes, err := hex.DecodeString(keyValues[storage.KeyLastRandomPrivKeyInHex].(string))
		if err != nil {
			return fmt.Errorf("error decoding server private key: %w", err)
		}

		privKey, err := ecdh.P256().NewPrivateKey(privKeyBytes)
		if err != nil {
			return fmt.Errorf("error initializing server private key: %w", err)
		}

		timeStamp := time.Unix(keyValues[storage.KeyTimeSinceLastChallengeUnixMillis].(int64), 0)

		m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
			privateKey: privKey,
			timeStamp:  timeStamp,
		}
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	if keyValues[storage.KeySecurityCardPairingSlot] != nil {
		m.pairingSlot = uint16(keyValues[storage.KeySecurityCardPairingSlot].(int32))
	}

	if keyValues[storage.KeySecurityCardUsageCount] != nil {
		m.securityCardUsageCount = uint16(keyValues[storage.KeySecurityCardUsageCount].(int32))
	}

	if keyValues[storage.KeySecurityCardReplayCounter] != nil {
		m.replayCounter = uint16(keyValues[storage.KeySecurityCardReplayCounter].(int32))
	}

	if keyValues[storage.KeySecretCardBytesInHex] != nil {
		secretCard, err := hex.DecodeString(keyValues[storage.KeySecretCardBytesInHex].(string))
		if err != nil {
			return fmt.Errorf("error decoding secret card in hex: %w", err)
		}
		copy(m.secretCardBytes[:], secretCard)
	}

	return nil
}
