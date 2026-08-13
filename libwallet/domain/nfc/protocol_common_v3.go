package nfc

import (
	"bytes"
	"io"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/cryptography"
)

// ParseMetadataV3 decodes the GET_METADATA wire format for V3 cards.
// Layout:
//
//	attestation_pub (65) || vendor (2) || model (2) || firmware (2) ||
//	capabilities (2) || op_counter (2) || provider_pub (65) ||
//	sig_len (1) || provider_sig (0..72)
//
// The provider section is always present; before STORE_CERTIFICATE the
// card writes 65 zero bytes for provider_pub and sig_len = 0. After
// provisioning, sig_len announces the trailing signature length.
//
// The exact wire bytes that produced the result are copied into
// metadata.RawBytes. Callers verifying the pairing MAC server-side must
// reuse those bytes verbatim; re-serializing the struct fields can
// change byte order or padding and break the MAC.
func ParseMetadataV3(data []byte) (*CardMetadataV3, error) {
	if len(data) < MetadataMinSizeV3 {
		return nil, errors.Errorf(
			"invalid metadata length: %d (expected at least %d)",
			len(data), MetadataMinSizeV3,
		)
	}

	reader := bytes.NewReader(data)
	metadata := &CardMetadataV3{}

	// Read attestation public key (65 bytes)
	if _, err := io.ReadFull(reader, metadata.AttestationPub[:]); err != nil {
		return nil, errors.Errorf("read attestation pub: %w", err)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(metadata.AttestationPub[:]); err != nil {
		return nil, errors.Errorf("invalid attestation pub: %w", err)
	}

	// Read card vendor (2 bytes)
	if _, err := io.ReadFull(reader, metadata.CardVendor[:]); err != nil {
		return nil, errors.Errorf("read card vendor: %w", err)
	}

	// Read card model (2 bytes)
	if _, err := io.ReadFull(reader, metadata.CardModel[:]); err != nil {
		return nil, errors.Errorf("read card model: %w", err)
	}

	// Read firmware version (2 bytes)
	if _, err := io.ReadFull(reader, metadata.FirmwareVersion[:]); err != nil {
		return nil, errors.Errorf("read firmware version: %w", err)
	}

	// Read card capabilities (2 bytes)
	if _, err := io.ReadFull(reader, metadata.Capabilities[:]); err != nil {
		return nil, errors.Errorf("read capabilities: %w", err)
	}

	// Read operation count (2 bytes, big-endian)
	operationBytes := make([]byte, 2)
	if _, err := io.ReadFull(reader, operationBytes); err != nil {
		return nil, errors.Errorf("read operation count: %w", err)
	}
	metadata.OperationCount = uint16(operationBytes[0])<<8 | uint16(operationBytes[1])

	if _, err := io.ReadFull(reader, metadata.ProviderPub[:]); err != nil {
		return nil, errors.Errorf("read provider pub: %w", err)
	}

	// Read provider signature length (1 byte)
	sigLenByte := make([]byte, 1)
	if _, err := io.ReadFull(reader, sigLenByte); err != nil {
		return nil, errors.Errorf("read provider sig length: %w", err)
	}
	metadata.ProviderSigLen = sigLenByte[0]

	if metadata.ProviderSigLen > MaxProviderSigSize {
		return nil, errors.Errorf(
			"provider sig length %d exceeds max %d",
			metadata.ProviderSigLen, MaxProviderSigSize,
		)
	}

	if metadata.ProviderSigLen > 0 {
		// Read provider signature (variable length 0..72 bytes)
		metadata.ProviderSig = make([]byte, metadata.ProviderSigLen)
		if _, err := io.ReadFull(reader, metadata.ProviderSig); err != nil {
			return nil, errors.Errorf("read provider sig: %w", err)
		}
	}

	// Snapshot the exact wire bytes the card emitted into RawBytes for
	// MAC verification.
	totalMetadataLen := MetadataMinSizeV3 + int(metadata.ProviderSigLen)
	metadata.RawBytes = make([]byte, totalMetadataLen)
	copy(metadata.RawBytes, data[:totalMetadataLen])

	return metadata, nil
}

// parsePairingResponseV3 decodes the PAIR response wire format:
//
//	P (65) || index (2) || metadata (141..213) || MAC (32) = 240..312 bytes.
//
// The firmware places the MAC LAST ("MAC is last so a parser can always
// find it at [len-32..len]"), with the variable-length metadata sitting
// between the fixed P||index header and the trailing MAC. Metadata is
// self-delimiting via its sig_len byte.
func parsePairingResponseV3(data []byte) (*PairingResponseV3, error) {
	// fixedHeader is P (65) + index (2); metadata and the trailing MAC
	// follow it.
	fixedHeader := Secp256R1PointSize + PairIndexSize
	minLen := fixedHeader + MetadataMinSizeV3 + MacSize
	if len(data) < minLen {
		return nil, errors.Errorf(
			"invalid v3 pair response length: %d (expected at least %d)",
			len(data), minLen,
		)
	}

	response := &PairingResponseV3{
		CardPublicKey: make([]byte, Secp256R1PointSize),
		MAC:           make([]byte, MacSize),
	}

	copy(response.CardPublicKey, data[:Secp256R1PointSize])
	response.Index = uint16(data[Secp256R1PointSize])<<8 | uint16(data[Secp256R1PointSize+1])

	if err := cryptography.ValidateSecp256r1PublicKey(response.CardPublicKey); err != nil {
		return nil, errors.Errorf("invalid card public key in v3 pair response: %w", err)
	}

	// Metadata is self-delimited (its sig_len byte gives its exact length),
	// so parse it right after the fixed header; the MAC is the 32 bytes
	// immediately after the metadata.
	metadata, err := ParseMetadataV3(data[fixedHeader:])
	if err != nil {
		return nil, errors.Errorf("parse metadata in v3 pair response: %w", err)
	}
	response.Metadata = *metadata

	// Under MAC-last framing the MAC is positioned by the metadata length,
	// so the response must be exactly header + metadata + MAC. Trailing
	// bytes would shift the MAC and be read as a corrupted one, so reject
	// them instead of silently mangling verification.
	macStart := fixedHeader + len(metadata.RawBytes)
	if len(data) != macStart+MacSize {
		return nil, errors.Errorf(
			"invalid v3 pair response length: %d (expected %d for a %d-byte metadata)",
			len(data), macStart+MacSize, len(metadata.RawBytes),
		)
	}
	copy(response.MAC, data[macStart:macStart+MacSize])

	return response, nil
}

// buildSignChallengeDataV3 constructs the data payload for a sign challenge command.
// It assembles the challenge parameters into a binary format expected by the card.
// The data format is: C || counter || index || has_more || reason || mac.
//
// Parameters:
//   - serverC: Server's ephemeral public key (65 bytes)
//   - counter: Usage counter for replay protection (2 bytes, big-endian)
//   - index: Pairing slot index (2 bytes, big-endian)
//   - reason: Variable-length operation reason/context data
//   - mac: HMAC-SHA256 authentication code (32 bytes)
func buildSignChallengeDataV3(
	serverC []byte,
	counter uint16,
	index uint16,
	reason []byte,
	mac []byte,
) []byte {
	// hasMoreSingleChunk is the has_more byte for a single-shot (non
	// streaming) challenge. The firmware reads it right after the
	// index to pick the single vs streaming path, and expects it even when
	// the whole reason fits in one command.
	const hasMoreSingleChunk = 0x00

	capacity := Secp256R1PointSize + CounterSize + PairIndexSize + 1 + len(reason) + MacSize
	data := make([]byte, 0, capacity)
	data = append(data, serverC...)              // C (65 bytes)
	data = append(data, IntTo2Bytes(counter)...) // counter (2 bytes)
	data = append(data, IntTo2Bytes(index)...)   // index (2 bytes)
	data = append(data, hasMoreSingleChunk)      // has_more = 0 (1 byte)
	data = append(data, reason...)               // reason (variable)
	data = append(data, mac...)                  // mac (32 bytes)
	return data
}

// parseSignChallengeResponseV3 decodes the SIGN_CHALLENGE response wire
// format: P (65) || MAC (32) = 97 bytes.
func parseSignChallengeResponseV3(data []byte) (*ChallengeResponseV3, error) {
	if len(data) != SignChallengeResponseSize {
		return nil, errors.Errorf(
			"invalid v3 sign challenge response length: %d (expected %d)",
			len(data), SignChallengeResponseSize,
		)
	}

	result := &ChallengeResponseV3{
		CardPublicKey: make([]byte, Secp256R1PointSize),
		MAC:           make([]byte, MacSize),
	}
	copy(result.CardPublicKey, data[:Secp256R1PointSize])
	copy(result.MAC, data[Secp256R1PointSize:])

	if err := cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey); err != nil {
		return nil, errors.Errorf("invalid card public key in v3 challenge response: %w", err)
	}

	return result, nil
}
