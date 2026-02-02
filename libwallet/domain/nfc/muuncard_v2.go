package nfc

import (
	"errors"
	"fmt"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/cryptography"
	"github.com/muun/libwallet/domain/model/security_card"
)

// Implementation to interact with our reference security card firmware v2.

const MuuncardV2AppletId = "A00000015100133900"

// Muuncard V2 specific APDU bytes.
const insMuuncardV2Setup = 0x10
const insMuuncardV2SignChallenge = 0x20
const insMuuncardV2ContinueChallenge = 0x21
const insMuuncardV2GetVersion = 0x70
const insMuuncardV2GetMetadata = 0x80

// MuuncardV2 specific status words.
const swMuuncardV2WrongLength = 0x6700
const swMuuncardV2ResponseTooLarge = 0x6B11
const swMuuncardV2InvalidPubKey = 0x6B12
const swMuuncardV2CryptoError = 0x6B14
const swMuuncardV2NoSlotsAvailable = 0x6B16
const swMuuncardV2InvalidMac = 0x6B17
const swMuuncardV2InvalidCounter = 0x6B18
const swMuuncardV2SlotNotPaired = 0x6B19

const (
	Secp256R1PointSize        = 65
	PairingSlotSize           = 2
	MetadataSize              = 75
	MacSize                   = 32
	TotalPairInputSize        = Secp256R1PointSize * 2                                        // C + pub_client = 130 bytes
	PairResponseSize          = Secp256R1PointSize + PairingSlotSize + MetadataSize + MacSize // 174 bytes
	SignChallengeResponseSize = Secp256R1PointSize + MacSize                                  // 97 bytes
	MaxApduSize               = 255
)

type MuunCardV2 struct {
	rawCard *JavaCard
}

type AppletVersion struct {
	Vendor string
	Major  byte
	Minor  byte
}

type PairingResponse struct {
	CardPublicKey   []byte        // 65 bytes - Card's ephemeral public key
	PairingSlot     []byte        // 2 bytes - Random pairing identifier
	Metadata        *CardMetadata // 75 bytes - Metadata including global_pub_card
	MAC             []byte        // 32 bytes - HMAC-SHA256 authentication
	GlobalSignature []byte        // 70-72 bytes - DER-Encoded ECDSA signature with global private key
}

type CardMetadata struct {
	GlobalPubCard   [65]byte // 65 bytes - Card's permanent public key
	CardVendor      [2]byte  // 2 bytes - Vendor name
	CardModel       [2]byte  // 2 bytes - Model name
	FirmwareVersion [2]byte  // 2 bytes - Firmware version
	UsageCount      uint16   // 2 bytes - Number of operations performed
	LanguageCode    [2]byte  // 2 bytes - Language preference
}

type ChallengeResponse struct {
	CardPublicKey []byte // P (65 bytes)
	MAC           []byte // 32 bytes
}

func NewCardV2(nfcBridge app_provided_data.NfcBridge) *MuunCardV2 {
	return &MuunCardV2{rawCard: newJavaCard(nfcBridge)}
}

var cardV2StatusToError = map[uint16]*CardError{
	swMuuncardV2WrongLength:      {Message: "card rejected input: wrong length", Code: ErrInternal},
	swMuuncardV2InvalidPubKey:    {Message: "card rejected public key: invalid format", Code: ErrInternal},
	swMuuncardV2ResponseTooLarge: {Message: "response too large, exceeds APDU limit of 255 bytes", Code: ErrInternal},
	swMuuncardV2CryptoError:      {Message: "cryptographic error during pairing", Code: ErrInternal},
	swMuuncardV2NoSlotsAvailable: {Message: "no pairing slots available on card", Code: ErrSlotOccupied},
	swMuuncardV2InvalidMac:       {Message: "invalid MAC", Code: ErrInternal},
	swMuuncardV2InvalidCounter:   {Message: "invalid counter", Code: ErrInternal},
	swMuuncardV2SlotNotPaired:    {Message: "slot not paired", Code: ErrSlotNotInitialized},
}

func (c *MuunCardV2) GetVersion() (*AppletVersion, error) {

	apdu := newAPDU(
		claEdge,
		insMuuncardV2GetVersion,
		nullByte,
		nullByte,
		[]byte{},
	)

	response, err := c.rawCard.transmit(apdu.serialize())
	if err != nil {
		return nil, fmt.Errorf("failed to transmit insMuuncardV2GetVersion: %v", err)
	}

	if response.StatusCode != responseOk {
		return nil, fmt.Errorf("failed with status: %04X", response.StatusCode)
	}

	if len(response.Response) < 8 {
		return nil, errors.New("response too short")
	}

	vendor := string(response.Response[:6]) // "MuunV2"
	major := response.Response[6]
	minor := response.Response[7]

	return &AppletVersion{
		Vendor: vendor,
		Major:  major,
		Minor:  minor,
	}, nil
}

func (c *MuunCardV2) GetMetadata() (*CardMetadata, error) {

	apdu := newAPDU(
		claEdge,
		insMuuncardV2GetMetadata,
		nullByte,
		nullByte,
		[]byte{},
	)

	response, err := c.rawCard.transmit(apdu.serialize())
	if err != nil {
		return nil, fmt.Errorf("failed to transmit insMuuncardV2GetMetadata: %v", err)
	}

	if response.StatusCode != responseOk {
		return nil, fmt.Errorf("failed with status: %04X", response.StatusCode)
	}

	metadata, err := parseMetadata(response.Response)
	if err != nil {
		return nil, fmt.Errorf("failed to parse metadata: %v", response.Response)
	}

	return metadata, nil
}

func (c *MuunCardV2) Pair(serverRandomPublicKey, clientPublicKey []byte) (*PairingResponse, error) {
	// Validate server random public key format (C)
	err := cryptography.ValidateSecp256r1PublicKey(serverRandomPublicKey)
	if err != nil {
		return nil, fmt.Errorf("invalid server random public key: %w", err)
	}

	// Validate client public key format (pub_client)
	err = cryptography.ValidateSecp256r1PublicKey(clientPublicKey)
	if err != nil {
		return nil, fmt.Errorf("invalid client public key: %w", err)
	}

	// Send C || pub_client to card (130 bytes total)
	input := make([]byte, 0, TotalPairInputSize)
	input = append(input, serverRandomPublicKey...) // C (65 bytes)
	input = append(input, clientPublicKey...)       // pub_client (65 bytes)

	apdu := newAPDU(
		claEdge,
		insMuuncardV2Setup,
		nullByte,
		nullByte,
		input,
	)

	response, err := c.transmit(apdu.serialize())
	if err != nil {
		return nil, fmt.Errorf("failed to transmit insMuuncardV2Setup: %w", err)
	}

	return parsePairingResponse(response.Response)
}

func (c *MuunCardV2) SignChallenge(
	challenge *security_card.SecurityCardSignChallenge,
	reason []byte,
) (*ChallengeResponse, error) {

	// Calculate maximum reason size for single chunk
	// Format: C(65) + count(2) + index(2) + has_more_chunks(1) + reason + mac(32) = 102 + reason
	// Max APDU = 255, so max single reason = 255 - 102 = 153 bytes
	maxSingleReasonSize := MaxApduSize - 65 - 2 - 2 - 1 - 32 // 153 bytes

	if len(reason) <= maxSingleReasonSize {
		return c.signChallengeSingle(challenge, reason)
	} else {
		return nil, errors.New("SignChallengeStreaming not implemented yet")
	}
}

func (c *MuunCardV2) signChallengeSingle(
	challenge *security_card.SecurityCardSignChallenge,
	reason []byte,
) (*ChallengeResponse, error) {

	data := buildSignChallengeData(
		challenge.ServerPublicKey, // C (65 bytes)
		challenge.CardUsageCount,  // counter (2 bytes, big-endian)
		challenge.PairingSlot,     // pairingSlot (2 bytes, big-endian)
		0,                         // has_more_chunks = 0 (single chunk)
		reason,                    // reason
		challenge.Mac,             // mac (32 bytes)
	)
	apdu := buildSignChallengeAPDU(data)

	response, err := c.transmit(apdu.serialize())
	if err != nil {
		return nil, fmt.Errorf("failed to transmit Sign Challenge: %w", err)
	}

	return parseSignChallengeResponse(response)
}

func (c *MuunCardV2) transmit(apdu []byte) (*CardResponse, error) {

	err := c.rawCard.selectApplet(MuuncardV2AppletId)
	if err != nil {
		return nil, newCardError(ErrAppletIdNotFound, "error selecting muuncard applet")
	}

	resp, err := c.rawCard.transmit(apdu)
	if err != nil {
		return nil, fmt.Errorf("error transmitting APDU: %w", err)
	}

	if resp.StatusCode != responseOk {
		return nil, mapStatusToCardV2Error(resp.StatusCode)
	}

	return resp, nil
}

func mapStatusToCardV2Error(code uint16) error {
	if cardError, ok := cardV2StatusToError[code]; ok {
		return cardError
	}
	return newCardError(ErrInternal, fmt.Sprintf("unknown error code: 0x%x", code))
}
