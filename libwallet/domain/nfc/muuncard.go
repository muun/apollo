package nfc

import (
	"crypto/sha1"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"log/slog"
)

// Implementation to interact with our reference security card firmware.

const muuncardAppletId = "A00000015100133700"

// claEdge is our Cla custom Instruction Class. You use 0x80-0xFF for applet-specific or Javacard
// proprietary commands.
const claEdge = 0x80

// Muuncard specific apdu bytes.
const insMuuncardSetup = 0x10
const insMuuncardReset = 0x30
const insMuuncardInitSecureChannel = 0x40
const insMuuncardSignMessage = 0x80

// Muuncard specific status words.
const swMuuncardInvalidSlot = 0x6B01
const swMuuncardSlotOccupied = 0x6B02
const swMuuncardKeyNotInitialized = 0x6B03
const swMuuncardKeyBuilderError = 0x6B04
const swMuuncardMacMismatch = 0x6B05
const swMuuncardHmacErrorBufferOverflow = 0x6884
const swMuuncardSecureChannelNotInitialized = 0x6985

type MuunCard struct {
	rawCard *SmartCard
}

func NewCard(nfcBridge app_provided_data.NfcBridge) *MuunCard {
	return &MuunCard{rawCard: newSmartCard(nfcBridge)}
}

// CardErrorCode represents our internal domain error codes
type CardErrorCode uint16

const (
	ErrInternal           CardErrorCode = 1
	ErrSlotOccupied       CardErrorCode = 2
	ErrSlotNotInitialized CardErrorCode = 3
)

var cardStatusToError = map[uint16]*CardError{
	swMuuncardSecureChannelNotInitialized: {Message: "secure channel is not initialized", Code: ErrInternal},
	swMuuncardSlotOccupied:                {Message: "slot already initialized", Code: ErrSlotOccupied},
	swMuuncardInvalidSlot:                 {Message: "invalid slot (out of range)", Code: ErrInternal},
	swMuuncardKeyBuilderError:             {Message: "key builder error (internal)", Code: ErrInternal},
	swMuuncardMacMismatch:                 {Message: "secure channel MAC doesn't match", Code: ErrInternal},
	swMuuncardKeyNotInitialized: {
		Message: "private key not initialized in given slot",
		Code:    ErrSlotNotInitialized,
	},
	swMuuncardHmacErrorBufferOverflow: {
		Message: "HMAC buffer overflow (block_size + message_length > data.length)",
		Code:    ErrInternal,
	},
}

func mapStatusToCardError(code uint16) error {
	if cardError, ok := cardStatusToError[code]; ok {
		return cardError
	}
	return newCardError(ErrInternal, fmt.Sprintf("unknown error code: 0x%x", code))
}

type CardError struct {
	Message string
	Code    CardErrorCode
}

// CardError implements the error interface for CustomError.
func (e *CardError) Error() string {
	return fmt.Sprintf("status %d: %s", e.Code, e.Message)
}

// newCardError creates a new CardError instance.
func newCardError(code CardErrorCode, message string) *CardError {
	return &CardError{
		Message: message,
		Code:    code,
	}
}

type ExtendedPublicKey struct {
	RawBytes []byte
}

// GenerateKeyPair sets up a fresh private key in the smart card. This command expects a 16-byte
// array to be used as part of the seed for the private key generation process.
func (c *MuunCard) GenerateKeyPair(seedBytes [16]byte) (*ExtendedPublicKey, error) {
	var cla byte = claEdge
	var ins byte = insMuuncardSetup
	var p2 byte = nullByte
	var slot byte = 0

	msg := newAPDU(cla, ins, slot, p2, seedBytes[:])
	serializedAPDU := msg.serialize()

	slog.Debug("Setting private key in card slot", slog.Any("slot", slot))

	resp, err := c.transmit(serializedAPDU)
	if err != nil {
		return nil, fmt.Errorf("error transmitting card setup apdu: %w", err)
	}

	if resp.StatusCode != responseOk {
		errorMsg := fmt.Sprintf("unknown status code %x, expecting ResponseOk", resp.StatusCode)
		slog.Warn(errorMsg, slog.Any("status code", resp.StatusCode))
		return nil, newCardError(ErrInternal, errorMsg)
	}

	slog.Debug("card private key has been set up", slog.Any("slot", slot))

	if len(resp.Response) != 82 {
		errorMsg := fmt.Sprintf(
			"invalid response length: %v, wanted 82. %s",
			len(resp.Response),
			hex.EncodeToString(resp.Response),
		)
		return nil, newCardError(ErrInternal, errorMsg)
	}

	xpub := resp.Response
	encodedXpub := base58.Encode(xpub)
	slog.Debug("xpub", slog.String("encodedXpub", encodedXpub))

	return &ExtendedPublicKey{RawBytes: xpub}, nil
}

// ResetCard resets an existing seed, effectively wiping out the related keys.
func (c *MuunCard) ResetCard() error {
	var cla byte = claEdge

	var ins byte = insMuuncardReset
	var p2 byte = nullByte
	var slot byte = 0

	msg := newAPDU(cla, ins, slot, p2, []byte{})
	serializedAPDU := msg.serialize()

	slog.Debug("Resetting slot", slog.Any("slot", slot))

	resp, err := c.transmit(serializedAPDU)
	if err != nil {
		return fmt.Errorf("error transmitting reset card apdu: %w", err)
	}

	if resp.StatusCode == responseOk {
		slog.Debug("card private key has been reset")

	} else if resp.StatusCode == swMuuncardKeyNotInitialized {
		return newCardError(swMuuncardKeyNotInitialized, "this slot was empty")
	}

	return nil
}

type SignedMessage struct {
	RawBytes []byte
}

// SignMessage signs a message hash (double SHA256) with the private key from the smart card.
// Requires a private key to be generated beforehand.
func (c *MuunCard) SignMessage(message string) (*SignedMessage, error) {
	var cla byte = claEdge

	var ins byte = insMuuncardSignMessage
	var p2 byte = nullByte
	var slot byte = 0

	msgHash := sha256.Sum256([]byte(message))
	msgHash = sha256.Sum256(msgHash[:])

	msg := newAPDU(cla, ins, slot, p2, msgHash[:])
	serializedAPDU := msg.serialize()

	resp, err := c.transmit(serializedAPDU)
	if err != nil {
		return nil, fmt.Errorf("error transmitting sign message apdu: %w", err)
	}

	if resp.StatusCode != responseOk {
		return nil, fmt.Errorf("couldn't sign message: %x", resp.StatusCode)
	}

	return &SignedMessage{RawBytes: resp.Response}, nil
}

// VerifySignature verifies a signature from a muuncard.
func (c *MuunCard) VerifySignature(
	hdPubKey *libwallet.HDPublicKey,
	message []byte,
	signedMessage []byte,
) (bool, error) {

	// TODO: Check whether the verification will only take place in Houston.
	ecPublicKey, err := hdPubKey.ECPubKey()
	if err != nil {
		return false, fmt.Errorf("error converting to EcPubKey: %w", err)
	}

	// Parse DER signature with ecdsa
	sig, err := ecdsa.ParseDERSignature(signedMessage)
	if err != nil {
		return false, fmt.Errorf("error parsing DER signature with ecdsa: %w", err)
	}

	// Double SHA256 + SHA1
	messageHash := sha256.Sum256(message)
	messageHash = sha256.Sum256(messageHash[:])
	finalMessageHash := sha1.Sum(messageHash[:])

	// Verify the signature using the SHA-1 digest of the double SHA-256 hash
	return sig.Verify(finalMessageHash[:], ecPublicKey), nil
}

func (c *MuunCard) transmit(apdu []byte) (*CardResponse, error) {

	err := c.rawCard.selectApplet(muuncardAppletId)
	if err != nil {
		return nil, fmt.Errorf("error selecting Muuuncard Applet: %w", err)
	}

	secureChannel, err := c.initSecureChannel()
	if err != nil {
		return nil, fmt.Errorf("error while initiating the secure channel transport layer: %w", err)
	}

	encryptedAPDU, err := secureChannel.encryptMessage(apdu)
	if err != nil {
		return nil, fmt.Errorf("error while encrypting message for secure channel: %w", err)
	}

	resp, err := c.rawCard.transmit(encryptedAPDU)
	if err != nil {
		return nil, fmt.Errorf("error transmitting encryptedAPDU: %w", err)
	}

	if resp.StatusCode != responseOk {
		return nil, mapStatusToCardError(resp.StatusCode)
	}

	if len(resp.Response) == 0 {
		return resp, nil
	}

	verifiedData, err := secureChannel.verifyResponseMAC(resp.Response)
	if err != nil {
		return nil, fmt.Errorf("MAC verification failed: %v", err)
	}
	resp.Response = verifiedData

	return resp, nil
}

func (c *MuunCard) initSecureChannel() (*muunCardSecureChannel, error) {
	var cla byte = claEdge

	var ins byte = insMuuncardInitSecureChannel
	var p1 byte = nullByte
	var p2 byte = nullByte

	priv, err := btcec.NewPrivateKey()
	if err != nil {
		return nil, fmt.Errorf("error generating keypair for secure channel: %w", err)
	}

	pub := priv.PubKey()
	serializedPubkey := pub.SerializeUncompressed()

	apdu := newAPDU(cla, ins, p1, p2, serializedPubkey)

	resp, err := c.rawCard.transmit(apdu.serialize())
	if err != nil {
		return nil, fmt.Errorf("error transmitting init secure channel apdu: %w", err)
	}

	if resp.StatusCode != responseOk {
		return nil, mapStatusToCardError(resp.StatusCode)
	}

	// This validation ended up being ugly as physical card firmware is currently returning more
	// data than required (incorrectly). We're keeping this for now to be compat with that. Will
	// change it in next firmware version.
	if !(len(resp.Response) == 65 || len(resp.Response) == 117) {
		errorMsg := fmt.Sprintf(
			"invalid response length: %v, wanted 65. %s",
			len(resp.Response),
			hex.EncodeToString(resp.Response),
		)
		return nil, newCardError(ErrInternal, errorMsg)
	}

	cardPubKeyBytes := resp.Response[:65]

	return newSecureChannel(priv, cardPubKeyBytes)
}
