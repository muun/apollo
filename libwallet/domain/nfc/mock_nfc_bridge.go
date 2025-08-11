package nfc

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"strings"
)

type MockMuunCard struct {
	network         *libwallet.Network
	secureChannel   *muunCardSecureChannel
	privateKeySlots []*libwallet.HDPrivateKey
}

func (s *muunCardSecureChannel) processSecureCommand(apdu []byte) ([]byte, error) {

	dataSize := int(apdu[iso7816OffsetLc])
	if dataSize < 20 {
		// TODO return error response SW_WRONG_LENGTH
		return nil, fmt.Errorf("incorrect data length %v", dataSize)
	}

	data := apdu[iso7816OffsetCData:]
	if len(data) != dataSize {
		return nil, fmt.Errorf("invalid apdu: expected %v data bytes, got %v", dataSize, len(data))
	}

	receivedMac := data[len(data)-hmacSha1SizeInBytes:]
	ciphertext := data[:len(data)-hmacSha1SizeInBytes]

	// Decrypt the payload with AES-CBC and no padding using a zero IV
	zeroIV := make([]byte, aes.BlockSize) // Zero IV
	key := s.derivedKey[:16]              // Use first 16 bytes for AES
	decryptedPlaintext, err := aesDecrypt(key, zeroIV, ciphertext)
	if err != nil {
		return nil, err
	}

	// Recompute the MAC using the derived key and encrypted data
	computedMac := computeHmacSha1(s.derivedKey, ciphertext) // Use full derived key for HMAC

	// Verify the MAC by comparing computed MAC with received MAC
	if !hmac.Equal(receivedMac, computedMac) {
		// TODO return error response SW_MAC_MISMATCH
		return nil, fmt.Errorf(
			"MAC verification failed, wanted %s, got %s",
			hex.EncodeToString(computedMac),
			hex.EncodeToString(receivedMac),
		)
	}

	return decryptedPlaintext, nil
}

type MockNfcBridge struct {
	mockCard *MockMuunCard
}

func NewMockNfcBridge(network *libwallet.Network) *MockNfcBridge {
	return &MockNfcBridge{
		mockCard: &MockMuunCard{
			network:         network,
			privateKeySlots: make([]*libwallet.HDPrivateKey, 1),
		},
	}
}

func (m *MockNfcBridge) Transmit(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return m.mockCard.processCommand(apdu)
}

func (c *MockMuunCard) processCommand(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	ins := apdu[iso7816OffsetIns]

	fmt.Printf("command apdu %s\n", hex.EncodeToString(apdu))

	switch ins {
	case insSelect:
		return c.handleSelectApplet(apdu)
	case insMuuncardSetup:
		return c.handleSetupCard(apdu)
	case insMuuncardReset:
		return c.handleResetCard(apdu)
	case insMuuncardInitSecureChannel:
		return c.handleInitSecureChannel(apdu)
	case insMuuncardSignMessage:
		return c.handleSignMessage(apdu)

	}

	// TODO return ISO7816.SW_INS_NOT_SUPPORTED
	return &app_provided_data.NfcBridgeResponse{
		Response:   nil,
		StatusCode: responseOk,
	}, nil
}

func (c *MockMuunCard) handleSelectApplet(apdu []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	dataSize := int(apdu[iso7816OffsetLc])
	data := apdu[iso7816OffsetCData:]

	if len(data) != dataSize {
		return nil, fmt.Errorf("invalid apdu: expected %v data bytes, got %v", dataSize, len(data))
	}

	// Handle deselect applet
	if dataSize == 0 {
		// return FCI template, contains several internal OS stuff like A000000151000000 (the ID of
		// globalplatform).
		return newSuccessResponse([]byte("6F108408A000000151000000A5049F6501FF")), nil
	}

	appletId := hex.EncodeToString(data)
	if strings.ToUpper(appletId) != muuncardAppletId {
		return nil, fmt.Errorf("incorrect applet id: %s", appletId)
	}

	// Return some internal OS stuff + "muun.com" in hex (e.g. 6D75756E2E636F6D).
	return newSuccessResponse([]byte("D1010855046D75756E2E636F6D")), nil
}

func (c *MockMuunCard) handleSetupCard(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {

	if c.secureChannel == nil {
		// TODO return SW_CONDITIONS_NOT_SATISFIED error response
	}

	// First thing, process secure command (if error, there's no sense in further processing)
	seed, err := c.secureChannel.processSecureCommand(apdu)
	if err != nil {
		return nil, err
	}

	// If MAC is verified, respond with the computed MAC (appended to response below).
	// Note: that is not what current muuncard impl does, it returns deriveKey as mac. So we
	// replicate that behavior. Will fix soon.
	derivedKey := c.secureChannel.derivedKey

	// We expect exactly 16 bytes
	if len(seed) != 16 {
		// TODO return SW_WRONG_LENGTH error response
	}

	slot := int(apdu[iso7816OffsetP1])
	if slot >= len(c.privateKeySlots) {
		// TODO return SW_INVALID_SLOT error response
	}

	if c.privateKeySlots[slot] != nil {
		return newErrorResponse(swMuuncardSlotOccupied), nil
	}

	// Generate random keypair
	generatedExtendedPrivateKey, err := libwallet.NewHDPrivateKey(seed, c.network)
	if err != nil {
		return nil, fmt.Errorf("failed to generate keypair: %w", err)
	}

	c.privateKeySlots[slot] = generatedExtendedPrivateKey
	generatedExtendedPublicKey := base58.Decode(generatedExtendedPrivateKey.PublicKey().String())

	// Reset secure channel so the client is forced to reinitialize it
	c.closeSecureChannel()

	return newSuccessResponse(append(generatedExtendedPublicKey, derivedKey...)), nil
}

func (c *MockMuunCard) handleResetCard(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {

	if c.secureChannel == nil {
		// TODO return SW_CONDITIONS_NOT_SATISFIED error response
	}

	slot := int(apdu[iso7816OffsetP1])
	if slot >= len(c.privateKeySlots) {
		// TODO return SW_INVALID_SLOT error response
	}

	if c.privateKeySlots[slot] == nil {
		return newErrorResponse(swMuuncardKeyNotInitialized), nil
	}

	c.privateKeySlots[slot] = nil

	return newSuccessResponse([]byte{}), nil
}

func (c *MockMuunCard) handleInitSecureChannel(apdu []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	dataLength := int(apdu[iso7816OffsetLc])
	pubKeyBytes := apdu[iso7816OffsetCData:]
	if len(pubKeyBytes) != dataLength || dataLength != 65 {
		return nil, errors.New("extended uncompressed pub key must have 65 bytes")
	}

	randomHDPriKey, _ := libwallet.NewHDPrivateKey(randomBytes(16), c.network)
	cardPrivateKey, err := randomHDPriKey.ECPrivateKey()
	if err != nil {
		return nil, err
	}

	secureChannel, err := newSecureChannel(cardPrivateKey, pubKeyBytes)
	if err != nil {
		return nil, err
	}

	c.secureChannel = secureChannel

	// Return 65 bytes uncompressed card ephemeral public key
	return newSuccessResponse(randomHDPriKey.PublicKey().SerializeUncompressed()), nil
}

func (c *MockMuunCard) handleSignMessage(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {

	if c.secureChannel == nil {
		// TODO return SW_CONDITIONS_NOT_SATISFIED error response
	}

	slot := int(apdu[iso7816OffsetP1])

	// Note this is inconsistent with what generateKeyPair/resetCard do (they return SW_INVALID_SLOT
	// if slot >= maxSlots), but it's what current muuncard impl does.
	if slot >= len(c.privateKeySlots) || c.privateKeySlots[slot] == nil {
		return newErrorResponse(swMuuncardKeyNotInitialized), nil
	}

	// This is also inconsistent with generateKeyPair do (first it processSecureCommand, then
	// validations), but it's what current muuncard impl does.
	messageHash, err := c.secureChannel.processSecureCommand(apdu)
	if err != nil {
		return nil, err
	}

	// We expect exactly 32 bytes
	if len(messageHash) != 32 {
		// TODO return error response SW_WRONG_LENGTH
	}

	// Sign the hash with private key of the given slot

	signingKey, err := c.privateKeySlots[slot].ECPrivateKey()
	if err != nil {
		return nil, err
	}

	finalMessageHash := sha1.Sum(messageHash[:])
	sig := ecdsa.Sign(signingKey, finalMessageHash[:])

	// If MAC is verified, respond with the computed MAC (appended to response below).
	// Note: that is not what current muuncard impl does, it returns deriveKey as mac. So we
	// replicate that behavior. Will fix soon.
	derivedKey := c.secureChannel.derivedKey

	return newSuccessResponse(append(sig.Serialize(), derivedKey...)), nil
}

func (c *MockMuunCard) closeSecureChannel() {
	if c.secureChannel == nil {
		// TODO return SW_CONDITIONS_NOT_SATISFIED error response
	}
	c.secureChannel = nil
}

func newSuccessResponse(responseBytes []byte) *app_provided_data.NfcBridgeResponse {

	fmt.Printf("response apdu %s\n", hex.EncodeToString(append(responseBytes, 0x90, 0x00)))

	return &app_provided_data.NfcBridgeResponse{
		Response:   responseBytes,
		StatusCode: responseOk,
	}
}

func newErrorResponse(statusCode int32) *app_provided_data.NfcBridgeResponse {

	fmt.Printf("response apdu %s\n", fmt.Sprintf("%x", statusCode))

	return &app_provided_data.NfcBridgeResponse{
		Response:   nil,
		StatusCode: statusCode,
	}
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

// aesDecrypt decrypts a ciphertext using the specified key and a IV.
// Note: this impl uses AES-128-CBC with no padding. Hence, key and IV must be 16 bytes (128 bits)
// and ciphertext length must be a multiple of 16.
func aesDecrypt(key []byte, iv []byte, ciphertext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create AES cipher: %w", err)
	}

	plaintext := make([]byte, len(ciphertext))

	mode := cipher.NewCBCDecrypter(block, iv)
	mode.CryptBlocks(plaintext, ciphertext)

	return plaintext, nil
}
