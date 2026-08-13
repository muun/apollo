package nfc

import (
	"context"
	"fmt"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/cryptography"
	"github.com/muun/libwallet/domain/model/security_card"
)

// Implementation to interact with our reference security card firmware v3.

// MuuncardV3AppletID identifies the V3 applet inside the card, used by
// the JavaCard SELECT command to route subsequent APDUs to it.
const MuuncardV3AppletID = "A00000015100133B00"

// Muuncard V3 specific APDU instruction bytes.
const (
	insMuuncardV3Pair          = 0x10
	insMuuncardV3SignChallenge = 0x20
	insMuuncardV3GetVersion    = 0x70
	insMuuncardV3GetMetadata   = 0x80
)

// Muuncard V3 specific status words.
const (
	swMuuncardV3WrongLength    = 0x6700
	swMuuncardV3InvalidPubKey  = 0x6B12
	swMuuncardV3CryptoError    = 0x6B14
	swMuuncardV3InvalidMac     = 0x6B17
	swMuuncardV3InvalidCounter = 0x6B18
	swMuuncardV3SlotNotPaired  = 0x6B19
)

// V3 wire format sizes.
const (
	Secp256R1PointSize = 65
	MacSize            = 32
	PairIndexSize      = 2
	CounterSize        = 2
	MaxProviderSigSize = 72 // DER-encoded P-256 ECDSA signature upper bound

	// PairResponseSizeV3 is P || index || MAC = 65 + 2 + 32 = 99.
	PairResponseSizeV3 = Secp256R1PointSize + PairIndexSize + MacSize

	// SignChallengeResponseSize is P || MAC = 65 + 32 = 97.
	SignChallengeResponseSize = Secp256R1PointSize + MacSize

	// MetadataMinSizeV3 is the metadata length when no provider
	// certificate has been provisioned (sig_len = 0). Full layout
	// is variable, 141..213 bytes:
	//   attestation_pub(65) + vendor(2) + model(2) + firmware(2) +
	//   capabilities(2) + op_counter(2) + provider_pub(65) +
	//   sig_len(1) + provider_sig(0..72)
	MetadataMinSizeV3 = Secp256R1PointSize + 2 + 2 + 2 + 2 + 2 + Secp256R1PointSize + 1
)

// MuunCardV3 is the long-lived client wired with the NFC bridge. It does
// not talk to the card until Connect() is called. Instantiate once per
// app and call Connect() for each NFC session.
type MuunCardV3 struct {
	rawCard *JavaCard
}

// MuunCardV3Session represents an open NFC session with the V3 applet
// already selected. Obtain one via MuunCardV3.Connect(). Calls on the
// session do not re-select the applet, so the cost of multiple APDUs
// in the same session is one SELECT plus one transmit per APDU.
//
// A session is bound to the underlying NFC session lifecycle. If the
// card is moved away the next call returns a transport error and the
// caller must obtain a fresh session via Connect() again.
type MuunCardV3Session struct {
	rawCard *JavaCard
}

// AppletVersionV3 mirrors the firmware GET_VERSION response (12 bytes:
// 6-byte vendor + 1-byte major + 1-byte minor + 4-byte commit hash).
type AppletVersionV3 struct {
	Vendor    string
	Major     byte
	Minor     byte
	GitCommit []byte
}

// PairingResponseV3 bundles what the server needs to verify a pairing:
// the card's ephemeral public key, the slot index, the MAC, and the
// post-pair metadata.
//
// With extended APDUs the card returns everything in one response:
// P || index || MAC || metadata (240..312 bytes). Metadata is always
// populated after a successful Pair(); the pairing MAC binds it, so
// the caller cannot make progress without it.
type PairingResponseV3 struct {
	CardPublicKey []byte         // P (65 bytes) - card's ephemeral public key
	Index         uint16         // 2 bytes
	MAC           []byte         // 32 bytes - HMAC over tag || C || P || index || metadata
	Metadata      CardMetadataV3 // post-pair metadata; always populated after Pair()
}

// ChallengeResponseV3 is what the card returns after a successful
// SIGN_CHALLENGE: a fresh ephemeral public key plus a MAC.
type ChallengeResponseV3 struct {
	CardPublicKey []byte // P (65 bytes) - fresh per challenge
	MAC           []byte // 32 bytes - HMAC over tag || C || P
}

// CardMetadataV3 contains the card's identity, status and certificate.
// layout: attestation_pub(65) || vendor(2) || model(2) || firmware(2) ||
// capabilities(2) || op_counter(2) || provider_pub(65) || sig_len(1) ||
// provider_sig(0..72).
// The total size is variable: 141 bytes when no certificate is provisioned,
// up to 213 bytes once one is.
//
// RawBytes holds the exact wire bytes the card emitted. The pairing MAC
// is computed over those bytes server-side, so callers verifying the MAC
// must use RawBytes verbatim instead of re-serializing the struct fields
// (any byte reshuffle or padding change breaks the MAC).
type CardMetadataV3 struct {
	AttestationPub  [65]byte // Card's permanent identity pubkey, set at factory
	CardVendor      [2]byte  // Vendor id, set at vault install
	CardModel       [2]byte  // Model id, set at vault install
	FirmwareVersion [2]byte  // Firmware version (e.g. 0x03 0x00 for 3.0)
	Capabilities    [2]byte  // Bitmap of card capabilities (e.g. has screen); opaque to firmware
	OperationCount  uint16   // Total crypto operations performed by the card
	ProviderPub     [65]byte // Card provider's pubkey
	ProviderSigLen  byte     // Length of ProviderSig in bytes
	ProviderSig     []byte   // Provider's ECDSA signature over AttestationPub

	RawBytes []byte // exact wire bytes; load-bearing input for the pairing MAC
}

// NewCardV3 builds a MuunCardV3 wired to the given NFC bridge. The
// returned client is long-lived and does not talk to the card until
// Connect is called.
func NewCardV3(nfcBridge app_provided_data.NfcBridge) *MuunCardV3 {
	return &MuunCardV3{rawCard: newJavaCard(nfcBridge)}
}

// Connect opens an NFC session by selecting the V3 applet on the card.
// Returns a *MuunCardV3Session to send subsequent APDUs without
// re-selecting. Call once per NFC session (typically once per logical
// operation like Pair or SignChallenge).
func (c *MuunCardV3) Connect(_ context.Context) (*MuunCardV3Session, error) {
	if err := c.rawCard.selectApplet(MuuncardV3AppletID); err != nil {
		return nil, newCardError(ErrAppletIdNotFound, "error selecting muuncard v3 applet")
	}
	return &MuunCardV3Session{rawCard: c.rawCard}, nil
}

var cardV3StatusToError = map[uint16]*CardError{
	swMuuncardV3WrongLength: {
		Message: "card rejected input: wrong length",
		Code:    ErrInternal,
	},
	swMuuncardV3InvalidPubKey: {
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
	swMuuncardV3SlotNotPaired: {
		Message: "slot is not paired",
		Code:    ErrSlotNotInitialized,
	},
}

// GetVersion reads the firmware version banner from the card: vendor
// string, major and minor numbers, and the git commit hash of the
// firmware build.
func (s *MuunCardV3Session) GetVersion(_ context.Context) (*AppletVersionV3, error) {
	apdu := newAPDU(claEdge, insMuuncardV3GetVersion, nullByte, nullByte, []byte{})

	response, err := s.transmit(apdu.serializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit get version: %w", err)
	}

	// Firmware always writes 12 bytes: 6 vendor + major + minor + 4 commit.
	if len(response.Response) < 12 {
		return nil, errors.Errorf(
			"get_version response too short: %d bytes (expected 12)",
			len(response.Response),
		)
	}

	return &AppletVersionV3{
		Vendor:    string(response.Response[:6]), // "MuunV3"
		Major:     response.Response[6],
		Minor:     response.Response[7],
		GitCommit: response.Response[8:12],
	}, nil
}

// GetMetadata reads the card's identity, status and certificate. The
// returned CardMetadataV3 preserves the exact wire bytes in RawBytes so
// the caller can verify the pairing MAC server-side without re-serializing.
func (s *MuunCardV3Session) GetMetadata(_ context.Context) (*CardMetadataV3, error) {
	apdu := newAPDU(claEdge, insMuuncardV3GetMetadata, nullByte, nullByte, []byte{})

	response, err := s.transmit(apdu.serializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit get metadata: %w", err)
	}

	metadata, err := ParseMetadataV3(response.Response)
	if err != nil {
		return nil, errors.Errorf("parse v3 metadata: %w", err)
	}

	return metadata, nil
}

// Pair runs the V3 pairing flow. The card derives secret_card from
// two Diffie-Hellman exchanges and returns its ephemeral public key,
// a MAC, and the metadata block — all in one extended-APDU response.
//
// Re-running Pair() bumps op_counter on the card, which can make a new
// card look already used.
func (s *MuunCardV3Session) Pair(
	_ context.Context,
	serverRandomPublicKey []byte,
) (*PairingResponseV3, error) {
	if err := cryptography.ValidateSecp256r1PublicKey(serverRandomPublicKey); err != nil {
		return nil, errors.Errorf("invalid server random public key: %w", err)
	}

	pairAPDU := newAPDU(
		claEdge,
		insMuuncardV3Pair,
		nullByte,
		nullByte,
		serverRandomPublicKey,
	)

	pairResponse, err := s.transmit(pairAPDU.serializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit pair: %w", err)
	}

	return parsePairingResponseV3(pairResponse.Response)
}

// SignChallenge runs the V3 challenge-response flow. The challenge
// fields (C, counter, index, reason, MAC) are produced by the server;
// the client just relays them to the card and returns the card's
// response.
//
// Extended APDUs let the whole payload — including a reason of any
// realistic size — travel in one command, so there is no dispatcher
// between single-chunk and streaming forms.
func (s *MuunCardV3Session) SignChallenge(
	_ context.Context,
	challenge *security_card.SecurityCardSignChallengeV3,
) (*ChallengeResponseV3, error) {
	if err := cryptography.ValidateSecp256r1PublicKey(challenge.ServerPublicKey); err != nil {
		return nil, errors.Errorf("invalid server public key: %w", err)
	}
	if len(challenge.MAC) != MacSize {
		return nil, errors.Errorf("mac must be %d bytes, got %d", MacSize, len(challenge.MAC))
	}

	data := buildSignChallengeDataV3(
		challenge.ServerPublicKey,
		challenge.Counter,
		challenge.Index,
		challenge.Reason,
		challenge.MAC,
	)

	apdu := newAPDU(claEdge, insMuuncardV3SignChallenge, nullByte, nullByte, data)

	response, err := s.transmit(apdu.serializeExtended())
	if err != nil {
		return nil, errors.Errorf("transmit sign challenge: %w", err)
	}

	return parseSignChallengeResponseV3(response.Response)
}

// transmit sends a single APDU over the already selected applet and
// fails on either a transport level error or a non-ok status code from
// the card.
//
// Transport errors are returned as *CardError with code ErrTransport so
// callers can discriminate them from protocol errors via errors.As.
func (s *MuunCardV3Session) transmit(apdu []byte) (*CardResponse, error) {
	resp, err := s.rawCard.transmit(apdu)
	if err != nil {
		return nil, newCardError(ErrTransport, fmt.Sprintf("nfc connection error: %s", err))
	}
	if resp.StatusCode != responseOk {
		return nil, mapStatusToCardV3Error(resp.StatusCode)
	}
	return resp, nil
}

func mapStatusToCardV3Error(code uint16) error {
	if cardError, ok := cardV3StatusToError[code]; ok {
		return cardError
	}
	return newCardError(ErrInternal, fmt.Sprintf("unknown v3 status: 0x%04X", code))
}
