package nfc

import (
	"encoding/binary"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
)

// ParsePairingResponseV3 decodes the PAIR response wire format:
//
//	P (65) || index (2) || metadata || MAC (32)
//
// The firmware places the MAC LAST ("MAC is last so a parser can always
// find it at [len-32..len]") so that the variable-length metadata between
// the fixed P||index header and the MAC can be cut out without reading it.
// Every firmware reports some metadata, and the MAC covers all of it, so
// what those bytes mean is left to the server that verifies the MAC.
func ParsePairingResponseV3(data []byte) (*PairingResponseV3, error) {
	// fixedHeader is P (65) + index (2); metadata and the trailing MAC
	// follow it.
	fixedHeader := Secp256r1PointSize + PairIndexSize
	minLen := fixedHeader + PairingMetadataMinSizeV3 + MACSize
	if len(data) < minLen {
		return nil, errors.Errorf(
			"invalid v3 pair response length: %d (expected at least %d)",
			len(data), minLen,
		)
	}

	response := &PairingResponseV3{
		CardPublicKey: make([]byte, Secp256r1PointSize),
		Metadata:      make([]byte, len(data)-fixedHeader-MACSize),
		MAC:           make([]byte, MACSize),
	}

	copy(response.CardPublicKey, data[:Secp256r1PointSize])
	response.Index = binary.BigEndian.Uint16(data[Secp256r1PointSize:])

	if err := cryptography.ValidateSecp256r1PublicKey(response.CardPublicKey); err != nil {
		return nil, errors.Errorf("invalid card public key in v3 pair response: %w", err)
	}

	macStart := len(data) - MACSize
	copy(response.Metadata, data[fixedHeader:macStart])
	copy(response.MAC, data[macStart:])

	return response, nil
}

// BuildSignChallengeDataV3 assembles a challenge request:
//
//	C(65) || call_counter(4) || index(2) || reason_len(2) || reason_chunk || mac(32)
//
// reasonTotalLength is what goes on the wire, and reasonChunk is what this
// command carries. The card routes on the declared total: a reason that fits
// alongside the header and the MAC in one APDU takes the single-shot path,
// and a longer one has to arrive in chunks, with that total saying when the
// chunks complete it.
func BuildSignChallengeDataV3(
	serverC []byte,
	callCounter uint32,
	index uint16,
	reasonTotalLength int,
	reasonChunk []byte,
	mac []byte,
) []byte {
	capacity := SignChallengeHeaderSizeV3 + len(reasonChunk) + MACSize
	data := make([]byte, 0, capacity)
	data = append(data, serverC...)                                       // C (65 bytes)
	data = binary.BigEndian.AppendUint32(data, callCounter)               // call_counter (4 bytes)
	data = binary.BigEndian.AppendUint16(data, index)                     // index (2 bytes)
	data = binary.BigEndian.AppendUint16(data, uint16(reasonTotalLength)) // reason_len (2 bytes)
	data = append(data, reasonChunk...)                                   // this chunk (variable)
	data = append(data, mac...)                                           // mac (32 bytes)
	return data
}

// ParseSignChallengeResponseV3 decodes the SIGN_CHALLENGE response:
//
//	P (65) || MAC (32) || sig_len (2) || commitment_sig (sig_len)
//
// The signature is variable-length because it is DER, so sig_len is what
// delimits it.
func ParseSignChallengeResponseV3(data []byte) (*ChallengeResponseV3, error) {
	if len(data) < SignChallengeResponseHeaderSizeV3 {
		return nil, errors.Errorf(
			"invalid v3 sign challenge response length: %d (expected at least %d)",
			len(data), SignChallengeResponseHeaderSizeV3,
		)
	}

	signatureLength := int(binary.BigEndian.Uint16(data[Secp256r1PointSize+MACSize:]))

	// No firmware omits the signature, and it is the only proof the card
	// approved the reason, so an absent one is a failure at the card boundary
	// rather than an empty signature to hand to Houston.
	if signatureLength == 0 {
		return nil, errors.New("v3 sign challenge response carries no commitment signature")
	}
	if signatureLength > MaxSignatureSizeV3 {
		return nil, errors.Errorf(
			"signature length %d exceeds the %d a DER P-256 signature can take",
			signatureLength, MaxSignatureSizeV3,
		)
	}

	totalLength := SignChallengeResponseHeaderSizeV3 + signatureLength
	if len(data) != totalLength {
		return nil, errors.Errorf(
			"invalid v3 sign challenge response length: %d (expected %d for a %d-byte signature)",
			len(data), totalLength, signatureLength,
		)
	}

	result := &ChallengeResponseV3{
		CardPublicKey: make([]byte, Secp256r1PointSize),
		MAC:           make([]byte, MACSize),
		CommitmentSig: make([]byte, signatureLength),
	}
	copy(result.CardPublicKey, data[:Secp256r1PointSize])
	copy(result.MAC, data[Secp256r1PointSize:Secp256r1PointSize+MACSize])
	copy(result.CommitmentSig, data[SignChallengeResponseHeaderSizeV3:totalLength])

	if err := cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey); err != nil {
		return nil, errors.Errorf("invalid card public key in v3 challenge response: %w", err)
	}

	return result, nil
}
