package nfc

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"github.com/muun/libwallet/cryptography"
	"io"
)

// parseMetadata parses raw card metadata bytes into a structured CardMetadata.
//
// The function expects exactly MetadataSize (75 bytes) containing card information in binary format
//   - Global Public Key (65 bytes): Secp256r1 point representing the card's global public key
//   - Card Vendor (2 bytes): Vendor identifier code
//   - Card Model (2 bytes): Model identifier code
//   - Firmware Version (2 bytes): Current firmware version information
//   - Usage Count (2 bytes, big-endian): Number of operations performed by the card
//   - Language Code (2 bytes): Preferred language setting for the card
//
// Parameters:
//   - data: Raw byte array containing exactly MetadataSize bytes of card metadata
//
// Returns:
//   - *CardMetadata: Structured metadata with parsed fields
//   - error: Parsing error if data length is invalid or field reading fails
func parseMetadata(data []byte) (*CardMetadata, error) {

	if len(data) < MetadataSize {
		return nil, fmt.Errorf(
			"invalid metadata length: %d (expected %d)", len(data), MetadataSize,
		)
	}

	reader := bytes.NewReader(data)
	metadata := &CardMetadata{}

	// Read global public key (65 bytes)
	_, err := io.ReadFull(reader, metadata.GlobalPubCard[:])
	if err != nil {
		return nil, fmt.Errorf("failed to read global public key")
	}

	err = cryptography.ValidateSecp256r1PublicKey(metadata.GlobalPubCard[:])
	if err != nil {
		return nil, fmt.Errorf("invalid card global public key: %w", err)
	}

	// Read card vendor (2 bytes)
	_, err = io.ReadFull(reader, metadata.CardVendor[:])
	if err != nil {
		return nil, fmt.Errorf("failed to read card vendor")
	}

	// Read card model (2 bytes)
	_, err = io.ReadFull(reader, metadata.CardModel[:])
	if err != nil {
		return nil, fmt.Errorf("failed to read card model")
	}

	// Read firmware version (2 bytes)
	_, err = io.ReadFull(reader, metadata.FirmwareVersion[:])
	if err != nil {
		return nil, fmt.Errorf("failed to read firmware version")
	}

	// Read usage count (2 bytes, big-endian)
	usageBytes := make([]byte, 2)
	_, err = io.ReadFull(reader, usageBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to read usage count")
	}
	metadata.UsageCount = uint16(usageBytes[0])<<8 | uint16(usageBytes[1])

	// Read language code (2 bytes)
	_, err = io.ReadFull(reader, metadata.LanguageCode[:])
	if err != nil {
		return nil, fmt.Errorf("failed to read language code")
	}

	return metadata, nil
}

// parsePairingResponse parses a raw pairing response byte array into a structured PairingResponse.
//
// The function expects the data to follow a specific binary format:
//   - Card Public Key (65 bytes): Secp256r1 point representing the card's public key
//   - Pairing Slot (2 bytes): Index identifying the pairing slot on the card
//   - Metadata (75 bytes): Card metadata information (parsed separately)
//   - MAC (32 bytes): Message Authentication Code for integrity verification
//   - Global Signature (variable, 70-72 bytes): Digital signature for authentication
//
// The total expected format is: P (65) || index (2) || metadata (75) || mac (32) || signature (variable)
//
// Parameters:
//   - data: Raw byte array containing the pairing response
//
// Returns:
//   - *PairingResponse: Structured response with parsed fields
//   - error: Parsing error if data is malformed, too short, or contains invalid values
//
// The function validates:
//   - Minimum data length (must be at least PairResponseSize)
//   - Card public key format (must be a valid Secp256r1 point)
//   - Global signature length (must be between 70-72 bytes)
func parsePairingResponse(data []byte) (*PairingResponse, error) {

	// TODO: this doesn't take into account signature size
	if len(data) < PairResponseSize {
		return nil, fmt.Errorf("response too short: %d bytes", len(data))
	}

	reader := bytes.NewReader(data)
	pairingResp := &PairingResponse{}

	// Read card public key
	pairingResp.CardPublicKey = make([]byte, Secp256R1PointSize)
	_, err := io.ReadFull(reader, pairingResp.CardPublicKey)
	if err != nil {
		return nil, fmt.Errorf("failed to read card public key: %w", err)
	}

	// Read pairing slot
	pairingResp.PairingSlot = make([]byte, PairingSlotSize)
	_, err = io.ReadFull(reader, pairingResp.PairingSlot)
	if err != nil {
		return nil, fmt.Errorf("failed to read pairing slot: %w", err)
	}

	// Read metadata
	metadataBytes := make([]byte, MetadataSize)
	_, err = io.ReadFull(reader, metadataBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to read metadata: %w", err)
	}

	metadata, err := parseMetadata(metadataBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to parse metadata: %v", metadataBytes)
	}
	pairingResp.Metadata = metadata

	// Read MAC
	pairingResp.MAC = make([]byte, MacSize)
	_, err = io.ReadFull(reader, pairingResp.MAC)
	if err != nil {
		return nil, fmt.Errorf("failed to read MAC: %w", err)
	}

	// Read remaining bytes as global signature
	remainingBytes, err := io.ReadAll(reader)
	if err != nil {
		return nil, fmt.Errorf("failed to read global signature: %w", err)
	}
	pairingResp.GlobalSignature = remainingBytes

	globalSignatureLength := len(pairingResp.GlobalSignature)
	if globalSignatureLength < 70 || globalSignatureLength > 72 {
		return nil, fmt.Errorf("invalid global signature length: %v", globalSignatureLength)
	}

	err = cryptography.ValidateSecp256r1PublicKey(pairingResp.CardPublicKey)
	if err != nil {
		return nil, fmt.Errorf("invalid card public key: %w", err)
	}

	return pairingResp, nil
}

// VerifyMetadata validates the integrity and correctness of card metadata.
// It performs validation checks on critical metadata fields to ensure the card
// is compatible and properly configured for use with the Muun wallet system.
//
// The function validates:
//   - GlobalPubCard: Must be a valid Secp256r1 public key (65 bytes, uncompressed format)
//   - FirmwareVersion: Must be exactly version 2.0 (0x02, 0x00)
//
// Other metadata fields (CardVendor, CardModel, UsageCount, LanguageCode) are
// enforced by their fixed-size array types and do not require additional validation.
//
// Returns an error if any validation check fails, nil if all checks pass.
func VerifyMetadata(metadata *CardMetadata) error {

	// All other fields are enforced with fixed-size array types

	err := cryptography.ValidateSecp256r1PublicKey(metadata.GlobalPubCard[:])
	if err != nil {
		return fmt.Errorf("expected valid GlobalPubCard, got 0x%02x", metadata.GlobalPubCard[0])
	}

	if metadata.FirmwareVersion[0] != 0x02 || metadata.FirmwareVersion[1] != 0x00 {
		return fmt.Errorf(
			"expected firmware version 2.0, got %d.%d",
			metadata.FirmwareVersion[0],
			metadata.FirmwareVersion[1],
		)
	}

	return nil
}

// parseSignChallengeResponse parses a card's sign challenge response into a structured
// ChallengeResponse.
//
// The function expects the response data to follow a specific binary format:
//   - Card Public Key (65 bytes): Secp256r1 point representing the card's ephemeral public key
//   - MAC (32 bytes): HMAC-SHA256 authentication code for response verification
//
// The total expected format is: P (65) || MAC (32) = 97 bytes (SignChallengeResponseSize)
//
// Parameters:
//   - response: CardResponse containing the raw response data from the card
//
// Returns:
//   - *ChallengeResponse: Structured response with parsed card public key and MAC
//   - error: Parsing error if data length is invalid or card public key validation fails
//
// The function validates:
//   - Exact response length (must be SignChallengeResponseSize = 97 bytes)
//   - Card public key format (must be a valid Secp256r1 point)
func parseSignChallengeResponse(response *CardResponse) (*ChallengeResponse, error) {

	if len(response.Response) != SignChallengeResponseSize {
		return nil, fmt.Errorf(
			"invalid sign challenge response length: %d", len(response.Response),
		)
	}

	err := cryptography.ValidateSecp256r1PublicKey(response.Response[:Secp256R1PointSize])
	if err != nil {
		return nil, fmt.Errorf("invalid card public key: %w", err)
	}

	return &ChallengeResponse{
		CardPublicKey: response.Response[:Secp256R1PointSize],
		MAC:           response.Response[Secp256R1PointSize:SignChallengeResponseSize],
	}, nil
}

// MakeChallengeSignMac computes a MAC for challenge-sign protocol authentication.
// It creates an HMAC-SHA256 using the shared secret to authenticate challenge parameters
// including the server's public key, usage counter, pairing slot, and operation reason.
// Only the first 16 bytes of the shared secret are used as the HMAC key (mac_secret_card)
// following the card protocol specification for key derivation.
//
// The MAC input follows the format: C || UsageCount || PairingSlot || Reason.
func MakeChallengeSignMac(
	sharedSecret []byte,
	challengeServerPublicKey []byte,
	reason []byte,
	counter uint16,
	pairingSlot uint16,
) []byte {

	macInput := make([]byte, 0, 65+2+2+len(reason))
	macInput = append(macInput, challengeServerPublicKey...) // C (65 bytes)
	macInput = append(macInput, IntTo2Bytes(counter)...)     // usage count (2 bytes)
	macInput = append(macInput, IntTo2Bytes(pairingSlot)...) // pairing slot (2 bytes)
	macInput = append(macInput, reason...)                   // reason (variable length)

	// Compute expected MAC using mac_secret_card (secret_card[:16])
	challengeMac := ComputeHMACSHA256(sharedSecret[:16], macInput)
	return challengeMac
}

// buildSignChallengeData constructs the data payload for a sign challenge command.
// It assembles the challenge parameters into a binary format expected by the card.
// The data format is: C || counter || index || has_more_chunks || reason || mac.
//
// Parameters:
//   - challengeC: Server's ephemeral public key (65 bytes)
//   - counter: Usage counter for replay protection (2 bytes, big-endian)
//   - index: Pairing slot index (2 bytes, big-endian)
//   - hasMoreChunks: Flag indicating if more data follows (0=single chunk, 1=streaming)
//   - reason: Variable-length operation reason/context data
//   - mac: HMAC-SHA256 authentication code (32 bytes)
func buildSignChallengeData(
	challengeC []byte,
	counter,
	index uint16,
	hasMoreChunks byte,
	reason,
	mac []byte,
) []byte {

	// Format: C(65) + counter(2) + index(2) + has_more_chunks(1) + reason + mac(32)
	data := make([]byte, 0, 65+2+2+1+len(reason)+32)
	data = append(data, challengeC...)                        // C (65 bytes)
	data = append(data, byte(counter>>8), byte(counter&0xFF)) // counter (2 bytes)
	data = append(data, byte(index>>8), byte(index&0xFF))     // index (2 bytes)
	data = append(data, hasMoreChunks)                        // has_more_chunks (1 byte)
	data = append(data, reason...)                            // reason (variable)
	data = append(data, mac...)                               // mac (32 bytes)
	return data
}

// buildSignChallengeAPDU wraps challenge data into a complete ISO 7816-4 APDU command.
// It constructs an APDU struct with the appropriate class, instruction, and parameters
// for the MuunCard V2 sign challenge operation.
//
// The APDU format is: CLA || INS || P1 || P2 || LC || DATA
// Where:
//   - CLA: Command class (0x00)
//   - INS: Sign challenge instruction code
//   - P1, P2: Parameter bytes (both 0x00)
//   - LC: Length of data payload (automatically calculated from data)
//   - DATA: The challenge data payload
//
// Returns an *apdu struct that can be serialized for transmission.
func buildSignChallengeAPDU(data []byte) *apdu {
	// Build APDU: CLA(1) + INS(1) + P1(1) + P2(1) + LC(1) + DATA
	return newAPDU(
		claEdge,                    // CLA
		insMuuncardV2SignChallenge, // INS
		nullByte,                   // P1
		nullByte,                   // P2
		data,                       // DATA (LC calculate from Data array)
	)
}

// ComputeHMACSHA256 computes HMAC-SHA256. It computes the HMAC (Hash-based Message Authentication
// Code) of the given data using the provided key and the SHA-256 hash function.
//
// It manually implements the HMAC algorithm as defined in RFC 2104:
//
//	HMAC(key, data) = SHA256((key ⊕ opad) || SHA256((key ⊕ ipad) || data))
//
// If the key is longer than the SHA-256 block size (64 bytes), it is first
// hashed with SHA-256. The resulting HMAC is returned as a byte slice.
//
// This function can be used to verify message integrity and authenticity when
// both parties share a secret key.
//
// Example:
//
//	mac := ComputeHMACSHA256([]byte("secret"), []byte("message"))
func ComputeHMACSHA256(key, data []byte) []byte {
	const blockSize = 64 // SHA-256 block size

	// Key preprocessing
	if len(key) > blockSize {
		hash := sha256.Sum256(key)
		key = hash[:]
	}

	// Pad key to block size
	paddedKey := make([]byte, blockSize)
	copy(paddedKey, key)

	// Create inner and outer padding
	innerPad := make([]byte, blockSize)
	outerPad := make([]byte, blockSize)

	for i := 0; i < blockSize; i++ {
		innerPad[i] = paddedKey[i] ^ 0x36
		outerPad[i] = paddedKey[i] ^ 0x5c
	}

	// Inner hash
	innerHash := sha256.New()
	innerHash.Write(innerPad)
	innerHash.Write(data)
	innerResult := innerHash.Sum(nil)

	// Outer hash
	outerHash := sha256.New()
	outerHash.Write(outerPad)
	outerHash.Write(innerResult)

	return outerHash.Sum(nil)
}

// IntTo2Bytes converts a 16-bit integer (int) to a two-byte slice.
// The function takes an integer 'n', extracts the high and low bytes,
// and returns a slice containing these two bytes in the order: high byte, low byte.
//
// Example:
//
//	n := 0x1234
//	result := IntTo2Bytes(n)
//	fmt.Println(result) // Output: [18 52] (0x12, 0x34)
func IntTo2Bytes(n uint16) []byte {
	hi := byte(n >> 8)
	lo := byte(n & 0xFF)
	return []byte{hi, lo}
}
