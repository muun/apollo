package nfc

import (
	"errors"
	"fmt"
	"github.com/muun/libwallet/app_provided_data"
)

// Implementation to interact with our reference security card firmware v2.

const MuuncardV2AppletId = "A00000015100133900"

// Muuncard V2 specific APDU bytes.
const insMuuncardV2Setup = 0x10

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
	Secp256R1PointSize = 65
	PairingSlotSize    = 2
	MetadataSize       = 75
	MacSize            = 32
	TotalPairInputSize = Secp256R1PointSize * 2                                        // C + pub_client = 130 bytes
	PairResponseSize   = Secp256R1PointSize + PairingSlotSize + MetadataSize + MacSize // 174 bytes
)

type MuunCardV2 struct {
	rawCard *SmartCard
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

func NewCardV2(nfcBridge app_provided_data.NfcBridge) *MuunCardV2 {
	return &MuunCardV2{rawCard: newSmartCard(nfcBridge)}
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

func (c *MuunCardV2) Pair(serverRandomPublicKey, clientPublicKey []byte) (*PairingResponse, error) {
	// Validate server random public key format (C)
	if len(serverRandomPublicKey) != Secp256R1PointSize {
		return nil, fmt.Errorf(
			"invalid server random public key length: %d (expected %d)",
			len(serverRandomPublicKey),
			Secp256R1PointSize,
		)
	}

	if serverRandomPublicKey[0] != 0x04 {
		return nil, fmt.Errorf(
			"invalid server random public key format: expected 0x04 prefix, got 0x%02X",
			serverRandomPublicKey[0],
		)
	}

	// Validate client public key format (C)
	if len(clientPublicKey) != Secp256R1PointSize {
		return nil, fmt.Errorf("invalid client public key length: %d (expected %d)",
			len(clientPublicKey),
			Secp256R1PointSize,
		)
	}

	if clientPublicKey[0] != 0x04 {
		return nil, fmt.Errorf(
			"invalid client public key format: expected 0x04 prefix, got 0x%02X",
			clientPublicKey[0],
		)
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

	// Parse response: P (65) || index (2) || metadata (75) || mac (32) || signature (variable)
	if len(response.Response) < PairResponseSize {
		return nil, fmt.Errorf("response too short: %d bytes", len(response.Response))
	}

	offset := 0
	pairingResp := &PairingResponse{
		CardPublicKey: response.Response[offset : offset+Secp256R1PointSize],
	}
	offset += Secp256R1PointSize

	pairingResp.PairingSlot = response.Response[offset : offset+PairingSlotSize]
	offset += PairingSlotSize

	pairingResp.Metadata = parseMetadata(response.Response[offset : offset+MetadataSize])
	offset += MetadataSize

	pairingResp.MAC = response.Response[offset : offset+MacSize]
	offset += MacSize

	// Remaining bytes are the signature
	pairingResp.GlobalSignature = response.Response[offset:]

	if len(pairingResp.GlobalSignature) < 70 || len(pairingResp.GlobalSignature) > 72 {
		return nil, errors.New("invalid global signature length")
	}

	if pairingResp.CardPublicKey[0] != 0x04 {
		return nil, fmt.Errorf(
			"invalid card public key format: expected 0x04 prefix, got 0x%02X",
			pairingResp.CardPublicKey[0],
		)
	}

	return pairingResp, nil
}

func parseMetadata(data []byte) *CardMetadata {
	if len(data) < MetadataSize {
		return nil
	}

	offset := 0

	metadata := &CardMetadata{}

	// Global public key (65 bytes)
	copy(metadata.GlobalPubCard[:], data[offset:offset+65])
	offset += 65

	// Card vendor (2 bytes)
	copy(metadata.CardVendor[:], data[offset:offset+2])
	offset += 2

	// Card model (2 bytes)
	copy(metadata.CardModel[:], data[offset:offset+2])
	offset += 2

	// Firmware version (2 bytes)
	copy(metadata.FirmwareVersion[:], data[offset:offset+2])
	offset += 2

	// Usage count (2 bytes, big-endian)
	metadata.UsageCount = uint16(data[offset])<<8 | uint16(data[offset+1])
	offset += 2

	// Language code (2 bytes)
	copy(metadata.LanguageCode[:], data[offset:offset+2])
	offset += 2

	return metadata
}

func (c *MuunCardV2) transmit(apdu []byte) (*CardResponse, error) {

	err := c.rawCard.selectApplet(MuuncardV2AppletId)
	if err != nil {
		return nil, fmt.Errorf("error selecting Muuncard Applet: %w", err)
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
