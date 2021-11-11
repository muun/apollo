package btcutilw

// This package wraps some methods from btcutil, using the same interface and delegating all
// supported cases to that module. It's written to be both compatible and similar in implementation,
// so it's easy to swap out in the future.

import (
	"fmt"
	"strings"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil"
)

// DecodeAddress uses btcutil.DecodeAddress for all cases except SegWit version 1, which is handled
// by this wrapper.
func DecodeAddress(addr string, defaultNet *chaincfg.Params) (btcutil.Address, error) {
	// Try to decode the address using btcutil:
	decoded, libErr := btcutil.DecodeAddress(addr, defaultNet)
	if libErr == nil {
		return decoded, nil
	}

	// If this is a Taproot address, we're here because the bech32 checksum failed. The easiest way
	// to know is to try:
	witnessVer, witnessProg, err := decodeSegWitAddressV1(addr)
	if err != nil {
		return nil, fmt.Errorf("failed to decode %s (%v after %w)", addr, err, libErr)
	}

	if witnessVer != 1 {
		return nil, btcutil.UnsupportedWitnessVerError(witnessVer)
	}

	if len(witnessProg) != 32 {
		return nil, btcutil.UnsupportedWitnessProgLenError(len(witnessProg))
	}

	oneIndex := strings.LastIndexByte(addr, '1')
	hrp := addr[:oneIndex]

	return newAddressTaprootKey(hrp, witnessProg)
}

// AddressTaprootKey is an Address for a keyspend-only P2TR output.
type AddressTaprootKey struct {
	hrp            string
	witnessVersion byte
	witnessProgram [32]byte
}

// NewAddressTaprootKey returns a new AddressTaprootKey.
func NewAddressTaprootKey(xOnlyPubKey []byte, net *chaincfg.Params) (*AddressTaprootKey, error) {
	if len(xOnlyPubKey) != 32 {
		return nil, fmt.Errorf("witness program must be 32 bytes for p2tr, not %d", len(xOnlyPubKey))
	}

	addr := &AddressTaprootKey{
		hrp:            net.Bech32HRPSegwit,
		witnessVersion: 0x01,
		witnessProgram: [32]byte{},
	}

	copy(addr.witnessProgram[:], xOnlyPubKey)

	return addr, nil
}

// EncodeAddress returns the bech32m string encoding of an AddressTaprootKey.
// Part of the Address interface.
func (a *AddressTaprootKey) EncodeAddress() string {
	str, err := encodeSegWitAddressV1(a.hrp, a.witnessVersion, a.witnessProgram[:])
	if err != nil {
		return ""
	}

	return str
}

// ScriptAddress returns the witness program for this address.
// Part of the Address interface.
func (a *AddressTaprootKey) ScriptAddress() []byte {
	return a.witnessProgram[:]
}

// IsForNet returns whether or not the AddressTaprootKey is associated with a network.
// Part of the Address interface.
func (a *AddressTaprootKey) IsForNet(net *chaincfg.Params) bool {
	return a.hrp == net.Bech32HRPSegwit
}

// String returns a human-readable string for the AddressTaprootKey.
// This is equivalent to calling EncodeAddress, but allows use of fmt.Stringer.
// Part of the Address interface.
func (a *AddressTaprootKey) String() string {
	return a.EncodeAddress()
}

// Hrp returns the human-readable part of the bech32 encoded AddressTaprootKey.
func (a *AddressTaprootKey) Hrp() string {
	return a.hrp
}

// WitnessVersion returns the witness version of the AddressTaprootKey.
func (a *AddressTaprootKey) WitnessVersion() byte {
	return a.witnessVersion
}

// WitnessProgram returns the witness program of the AddressTaprootKey.
func (a *AddressTaprootKey) WitnessProgram() []byte {
	return a.witnessProgram[:]
}

func newAddressTaprootKey(hrp string, witnessProg []byte) (*AddressTaprootKey, error) {
	if len(witnessProg) != 32 {
		return nil, fmt.Errorf("witness program must be 32 bytes for p2tr")
	}

	addr := &AddressTaprootKey{
		hrp:            strings.ToLower(hrp),
		witnessVersion: 0x01,
	}

	copy(addr.witnessProgram[:], witnessProg)

	return addr, nil
}
