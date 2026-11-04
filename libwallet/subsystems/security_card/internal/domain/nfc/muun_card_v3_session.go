package nfc

import (
	"context"
	"fmt"
	"math"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
)

// Muuncard V3 specific APDU instruction bytes.
const (
	INSMuuncardV3Pair              = 0x10
	INSMuuncardV3SignChallenge     = 0x20
	insMuuncardV3ContinueChallenge = 0x21
	insMuuncardV3GetVersion        = 0x70
	INSMuuncardV3GetMetadata       = 0x80
)

// Muuncard V3 specific status words.
const (
	SWMuuncardV3WrongLength    = 0x6700
	SWMuuncardV3InvalidPubKey  = 0x6B12
	swMuuncardV3CryptoError    = 0x6B14
	swMuuncardV3InvalidMac     = 0x6B17
	swMuuncardV3InvalidCounter = 0x6B18
	SWMuuncardV3SlotNotPaired  = 0x6B19

	// swMuuncardV3TooManyFailures means the card answered MAX_FAILURES
	// challenges in a row without one confirming the ratchet, so it refuses
	// until a new pairing. The cap is what bounds the traces a differential
	// side-channel attack can collect under one secret.
	swMuuncardV3TooManyFailures = 0x6B1A

	// SWMuuncardV3TooManyPairings means the card handed out the last of the
	// pairings it has for its life and will never pair again.
	SWMuuncardV3TooManyPairings = 0x6B1B

	swMuuncardV3StreamingNotStarted     = 0x6B20
	swMuuncardV3StreamingAlreadyStarted = 0x6B21

	SWMuuncardV3NotReady = 0x6B34

	// swMuuncardV3MoreDataNeeded is not a failure: the card is telling the
	// client it has taken this chunk of the reason and is waiting for the
	// rest.
	swMuuncardV3MoreDataNeeded = 0x9001
)

// V3 wire format sizes.
const (
	Secp256r1PointSize = 65
	MACSize            = 32
	PairIndexSize      = 2

	// VersionSizeV3 covers one version field, major(u16be) || minor(u16be).
	VersionSizeV3 = 4

	// PairingMetadataMinSizeV3 only asks the metadata to have some content.
	// Every firmware sends more, but a stricter minimum would depend on how a
	// firmware lays the metadata out, which the client does not read. Houston
	// reads it, and refuses metadata that is too short.
	PairingMetadataMinSizeV3 = 1

	// CallCounterSizeV3 is the width of the challenge request's replay
	// counter. Not to be confused with the metadata's challenge_counter,
	// which is two bytes and counts challenges the card answered.
	CallCounterSizeV3 = 4

	reasonLengthSizeV3 = 2

	// SignChallengeHeaderSizeV3 is everything the challenge request carries
	// ahead of the reason: C(65) || call_counter(4) || index(2) ||
	// reason_len(2).
	SignChallengeHeaderSizeV3 = Secp256r1PointSize + CallCounterSizeV3 +
		PairIndexSize + reasonLengthSizeV3

	// MaxReasonChunkSizeV3 is a transport limit, not a protocol one: the
	// reason bytes that fit alongside the header and the MAC in one short
	// APDU. A longer reason has to be streamed.
	MaxReasonChunkSizeV3 = MaxShortAPDUDataSize - SignChallengeHeaderSizeV3 - MACSize

	// MaxReasonSizeV3 is what reason_len can express: the card reads it as a
	// signed short and refuses a negative value.
	MaxReasonSizeV3 = math.MaxInt16

	signatureLengthSizeV3 = 2

	// MaxSignatureSizeV3 is the upper bound of a DER-encoded P-256 ECDSA
	// signature.
	MaxSignatureSizeV3 = 72

	// SignChallengeResponseHeaderSizeV3 is the fixed part the signature
	// follows: P(65) || MAC(32) || sig_len(2).
	SignChallengeResponseHeaderSizeV3 = Secp256r1PointSize + MACSize + signatureLengthSizeV3
)

// MuunCardV3Session represents an open NFC session with the V3 applet
// already selected. Obtain one via MuunCardV3.Connect(). Calls on the
// session do not re-select the applet, so the cost of multiple APDUs
// in the same session is one SELECT plus one transmit per APDU.
var cardV3StatusToError = map[uint16]*CardError{
	SWMuuncardV3WrongLength: {
		Message: "card rejected input: wrong length",
		Code:    ErrInternal,
	},
	SWMuuncardV3InvalidPubKey: {
		Message: "card rejected public key: invalid format",
		Code:    ErrInternal,
	},
	swMuuncardV3CryptoError: {
		Message: "cryptographic error during pairing or challenge",
		Code:    ErrInternal,
	},
	swMuuncardV3InvalidMac: {
		Message: "invalid MAC",
		Code:    ErrInternal,
	},
	swMuuncardV3InvalidCounter: {
		Message: "invalid counter",
		Code:    ErrInternal,
	},
	SWMuuncardV3SlotNotPaired: {
		Message: "slot is not paired",
		Code:    ErrSlotNotInitialized,
	},
	swMuuncardV3StreamingNotStarted: {
		Message: "card is not expecting more reason data",
		Code:    ErrInternal,
	},
	swMuuncardV3StreamingAlreadyStarted: {
		Message: "card is already receiving a reason",
		Code:    ErrInternal,
	},
	swMuuncardV3TooManyFailures: {
		Message: "card needs to be paired again: too many unconfirmed challenges",
		Code:    ErrCardMustPairAgain,
	},
	SWMuuncardV3TooManyPairings: {
		Message: "card has no pairings left",
		Code:    ErrCardOutOfPairings,
	},
	SWMuuncardV3NotReady: {
		Message: "card has not finished its initialization ceremony",
		Code:    ErrInternal,
	},
}

// errCardWantsMoreData means the card took the command and is waiting for the
// rest of a reason, which is flow control rather than a failure.
//
// It surfaces as an error so that the default is safe: only the streaming
// path expects it, and a command that never streams cannot mistake the card's
// empty acknowledgement for its answer.
var errCardWantsMoreData = errors.New("card is waiting for more reason data")

// A session is bound to the underlying NFC session lifecycle. If the
// card is moved away the next call returns a transport error and the
// caller must obtain a fresh session via Connect() again.
type MuunCardV3Session struct {
	rawCard *JavaCard

	// selectResponse is what the applet answered the selection this session
	// opened with. An applet that answers nothing is refused at Connect, so a
	// session always carries one.
	selectResponse *SelectResponseV3
}

// SelectResponse is what the applet said about itself when this session
// selected it: its firmware version and, from the firmware that sends one,
// its short device id.
func (s *MuunCardV3Session) SelectResponse() *SelectResponseV3 {
	return s.selectResponse
}

func (s *MuunCardV3Session) GetVersion(
	_ context.Context,
) (*AppletVersionV3, error) {
	apdu := NewAPDU(
		CLAEdge, insMuuncardV3GetVersion,
		NullByte, NullByte, []byte{},
	)

	response, err := s.transmit(apdu.SerializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit get version: %w", err)
	}

	if len(response.Response) < 12 {
		return nil, errors.Errorf(
			"get_version response too short: %d bytes (expected 12)",
			len(response.Response),
		)
	}

	return &AppletVersionV3{
		Vendor:    string(response.Response[:6]),
		Major:     response.Response[6],
		Minor:     response.Response[7],
		GitCommit: response.Response[8:12],
	}, nil
}

// Pair runs the V3 pairing flow. The card derives secret_card from
// two Diffie-Hellman exchanges and returns its ephemeral public key,
// a MAC, and the metadata block — all in one extended-APDU response.
//
// Re-running Pair() replaces the pairing the card holds — there is one slot
// today — so the secret from a previous pairing stops working.
func (s *MuunCardV3Session) Pair(
	_ context.Context,
	serverRandomPublicKey []byte,
) (*PairingResponseV3, error) {
	if err := cryptography.ValidateSecp256r1PublicKey(
		serverRandomPublicKey,
	); err != nil {
		return nil, errors.Errorf(
			"invalid server random public key: %w", err,
		)
	}

	pairAPDU := NewAPDU(
		CLAEdge,
		INSMuuncardV3Pair,
		NullByte,
		NullByte,
		serverRandomPublicKey,
	)

	pairResponse, err := s.transmit(pairAPDU.SerializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit pair: %w", err)
	}

	return ParsePairingResponseV3(pairResponse.Response)
}

// SignChallenge runs the V3 challenge-response flow. The challenge
// fields (C, call_counter, index, reason, MAC) are produced by the server;
// the client just relays them to the card and returns the card's
// response.
//
// The card routes on the reason's declared length: one that fits alongside
// the header and the MAC in a single APDU goes out whole, and a longer one
// is streamed in chunks.
func (s *MuunCardV3Session) SignChallenge(
	_ context.Context,
	challenge *model.SecurityCardSignChallengeV3,
) (*ChallengeResponseV3, error) {
	if err := cryptography.ValidateSecp256r1PublicKey(
		challenge.ServerPublicKey,
	); err != nil {
		return nil, errors.Errorf(
			"invalid server public key: %w", err,
		)
	}
	if len(challenge.MAC) != MACSize {
		return nil, errors.Errorf(
			"mac must be %d bytes, got %d",
			MACSize, len(challenge.MAC),
		)
	}
	// The card declares the reason's length as a signed short, so anything
	// past this cannot be expressed on the wire at all.
	if len(challenge.Reason) > MaxReasonSizeV3 {
		return nil, errors.Errorf(
			"reason is %d bytes, over the %d the wire can declare",
			len(challenge.Reason), MaxReasonSizeV3,
		)
	}

	// The card routes on the declared length, so the client has to send the
	// shape the card expects for it.
	if len(challenge.Reason) > MaxReasonChunkSizeV3 {
		return s.signChallengeStreaming(challenge)
	}

	data := BuildSignChallengeDataV3(
		challenge.ServerPublicKey,
		challenge.CallCounter,
		challenge.Index,
		len(challenge.Reason),
		challenge.Reason,
		challenge.MAC,
	)

	apdu := NewAPDU(
		CLAEdge, INSMuuncardV3SignChallenge,
		NullByte, NullByte, data,
	)

	response, err := s.transmit(apdu.SerializeExtended())
	if err != nil {
		return nil, errors.Errorf(
			"transmit sign challenge: %w", err,
		)
	}

	return ParseSignChallengeResponseV3(response.Response)
}

// signChallengeStreaming sends a reason too long for one APDU: a first
// command carrying the header and as much of the reason as fits, then bare
// chunks until the card has the length the header declared.
//
// The card answers SW_MORE_DATA_NEEDED to every command but the last, which
// carries the challenge response.
func (s *MuunCardV3Session) signChallengeStreaming(
	challenge *model.SecurityCardSignChallengeV3,
) (*ChallengeResponseV3, error) {
	firstChunkSize := min(MaxReasonChunkSizeV3, len(challenge.Reason))

	data := BuildSignChallengeDataV3(
		challenge.ServerPublicKey,
		challenge.CallCounter,
		challenge.Index,
		len(challenge.Reason),
		challenge.Reason[:firstChunkSize],
		challenge.MAC,
	)

	apdu := NewAPDU(CLAEdge, INSMuuncardV3SignChallenge, NullByte, NullByte, data)

	if err := s.transmitExpectingMore(apdu.SerializeExtended()); err != nil {
		return nil, errors.Errorf("transmit sign challenge: %w", err)
	}

	for reasonOffset := firstChunkSize; reasonOffset < len(challenge.Reason); {
		chunkSize := min(MaxShortAPDUDataSize, len(challenge.Reason)-reasonOffset)
		chunk := challenge.Reason[reasonOffset : reasonOffset+chunkSize]
		reasonOffset += chunkSize

		apdu := NewAPDU(CLAEdge, insMuuncardV3ContinueChallenge, NullByte, NullByte, chunk)

		// The card ends the stream where the declared length said it would,
		// so the chunks carry no terminator: the one that completes the
		// reason is the one that comes back with the answer.
		if reasonOffset < len(challenge.Reason) {
			if err := s.transmitExpectingMore(apdu.SerializeExtended()); err != nil {
				return nil, errors.Errorf("transmit continue challenge: %w", err)
			}
			continue
		}

		response, err := s.transmit(apdu.SerializeExtended())
		if err != nil {
			return nil, errors.Errorf("transmit last reason chunk: %w", err)
		}
		return ParseSignChallengeResponseV3(response.Response)
	}

	return nil, errors.New("card never answered the last reason chunk")
}

func (s *MuunCardV3Session) transmit(
	apdu []byte,
) (*CardResponse, error) {
	resp, err := s.rawCard.transmit(apdu)
	if err != nil {
		return nil, newCardError(
			ErrTransport,
			fmt.Sprintf("nfc connection error: %s", err),
		)
	}

	switch resp.StatusCode {
	case ResponseOK:
		return resp, nil
	case swMuuncardV3MoreDataNeeded:
		return nil, errCardWantsMoreData
	default:
		return nil, mapStatusToCardV3Error(resp.StatusCode)
	}
}

// transmitExpectingMore sends a command that must leave a stream open. The
// card asking for more is the success here, so anything else is reported as
// the failure it is: a real error, or the card answering a reason it has not
// finished receiving.
func (s *MuunCardV3Session) transmitExpectingMore(apdu []byte) error {
	_, err := s.transmit(apdu)
	switch {
	case errors.Is(err, errCardWantsMoreData):
		return nil
	case err != nil:
		return err
	default:
		return errors.New("card answered before the reason was complete")
	}
}

func mapStatusToCardV3Error(code uint16) error {
	if cardError, ok := cardV3StatusToError[code]; ok {
		return cardError
	}
	return newCardError(
		ErrInternal,
		fmt.Sprintf("unknown v3 status: 0x%04X", code),
	)
}
