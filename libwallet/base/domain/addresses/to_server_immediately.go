package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// ToServerImmediatelyOutput is the output paying the lightning server with no
// delay. It is a native segwit (P2WPKH) output with a single spending path:
//
//   - the lightning server's key: wpkh(lightningServer)
type ToServerImmediatelyOutput struct {
	LightningServerPubKey *btcec.PublicKey
	path                  string
	network               *chaincfg.Params
}

// NewToServerImmediatelyOutput derives the lightning server key to
// derivationPath and builds the output. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>.
func NewToServerImmediatelyOutput(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) (*ToServerImmediatelyOutput, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToServerImmediatelyOutput{
		LightningServerPubKey: keys.lightningServerPubKey,
		path:                  derivationPath,
		network:               baseKeys.Network(),
	}, nil
}

// Address returns the P2WPKH address paying to this output.
func (o *ToServerImmediatelyOutput) Address() (*WalletAddress, error) {
	pubKeyHash := btcutil.Hash160(o.LightningServerPubKey.SerializeCompressed())

	address, err := btcutil.NewAddressWitnessPubKeyHash(pubKeyHash, o.network)
	if err != nil {
		return nil, errors.Errorf("build witness pubkey hash address: %w", err)
	}

	return New(ToServerImmediately, o.path, address.EncodeAddress()), nil
}

// LongestToServerImmediatelyWitness returns a maximum-size witness for a spend
// of the output: the longest standard ECDSA signature (72 bytes, sighash byte
// included) and a compressed public key.
func LongestToServerImmediatelyWitness() (wire.TxWitness, error) {
	return wire.TxWitness{
		longestStandardECDSASignature(),
		make([]byte, btcec.PubKeyBytesLenCompressed),
	}, nil
}
