package addresses

import (
	"bytes"
	"testing"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

const (
	fixtureBasePath = "m/schema:1'/recovery:1'"
	fixturePath     = fixtureBasePath + "/external:1/0"
	fixtureTimelock = int64(144)
	fixtureAmount   = int64(100_000)
)

// setSpendDelaysForTest shortens every spend delay to blocks for the rest of
// the test, so its regtest spends mature, and restores the spec's values when
// it ends. Tests calling it can't run in parallel.
func setSpendDelaysForTest(t *testing.T, blocks int64) {
	t.Helper()

	previous := delays
	delays = spendDelays{
		toClientDelay:           blocks,
		toClientWithdrawalDelay: blocks,
		toServerDelay:           blocks,
	}
	t.Cleanup(func() { delays = previous })
}

// fixtureKey derives the deterministic key at fixturePath from a master key
// built with a single repeated byte. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest derives the same keys.
func fixtureKey(t *testing.T, fill byte) *hdkeychain.ExtendedKey {
	t.Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo(fixturePath)
	require.NoError(t, err)
	return derived.ExtendedKey()
}

// fixtureKeys derives the deterministic user, cosigner and lightningServer
// keys at fixturePath.
func fixtureKeys(t *testing.T) []*hdkeychain.ExtendedKey {
	t.Helper()

	return []*hdkeychain.ExtendedKey{
		fixtureKey(t, 0x01), fixtureKey(t, 0x02), fixtureKey(t, 0x03),
	}
}

// fixtureBasePrivateKey returns the deterministic private key at the schema
// base path from a master key built with a single repeated byte.
func fixtureBasePrivateKey(t *testing.T, fill byte) *hd_keys.HDPrivateKey {
	t.Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo(fixtureBasePath)
	require.NoError(t, err)
	return derived
}

// fixtureBasePrivateKeys returns the deterministic user, cosigner and
// lightningServer private keys at the schema base path.
func fixtureBasePrivateKeys(t *testing.T) []*hd_keys.HDPrivateKey {
	t.Helper()

	return []*hd_keys.HDPrivateKey{
		fixtureBasePrivateKey(t, 0x01),
		fixtureBasePrivateKey(t, 0x02),
		fixtureBasePrivateKey(t, 0x03),
	}
}

// fixtureKeyTriple returns the deterministic participant keys at fixturePath
// as a public key triple.
func fixtureKeyTriple(t *testing.T) *hd_keys.PublicKeyTriple {
	t.Helper()

	extendedKeys := fixtureKeys(t)
	publicKeys := make([]*hd_keys.HDPublicKey, len(extendedKeys))
	for i, key := range extendedKeys {
		neutered, err := key.Neuter()
		require.NoError(t, err)
		publicKeys[i] = hd_keys.NewHDPublicKey(
			neutered, fixturePath, &chaincfg.RegressionNetParams,
		)
	}

	return hd_keys.NewPublicKeyTriple(publicKeys[0], publicKeys[1], publicKeys[2])
}

// basePublicKeyForTest derives a base58 master private key to the schema base
// path and returns its public key, mirroring the base keys the apps hold:
// address paths extend the base path with non-hardened steps only, so they
// stay derivable from the public key.
func basePublicKeyForTest(t *testing.T, base58 string) *hd_keys.HDPublicKey {
	t.Helper()

	master, err := hd_keys.NewHDPrivateKeyFromString(base58, "m", &chaincfg.RegressionNetParams)
	require.NoError(t, err)
	derived, err := master.DeriveTo(fixtureBasePath)
	require.NoError(t, err)
	return derived.PublicKey()
}

// fixtureBaseKeyTriple returns the deterministic participant keys at the
// schema base path as a public key triple.
func fixtureBaseKeyTriple(t *testing.T) *hd_keys.PublicKeyTriple {
	t.Helper()

	publicKeys := make([]*hd_keys.HDPublicKey, 3)
	for i, fill := range []byte{0x01, 0x02, 0x03} {
		master := hd_keys.NewMasterHDPrivateKeyFromBytes(
			bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
		)
		derived, err := master.DeriveTo(fixtureBasePath)
		require.NoError(t, err)
		publicKeys[i] = derived.PublicKey()
	}

	return hd_keys.NewPublicKeyTriple(publicKeys[0], publicKeys[1], publicKeys[2])
}

// baseKeyTripleForTest builds the base-path public key triple of three base58
// master private keys.
func baseKeyTripleForTest(
	t *testing.T,
	userKey, cosignerKey, lightningServerKey string,
) *hd_keys.PublicKeyTriple {
	t.Helper()

	return hd_keys.NewPublicKeyTriple(
		basePublicKeyForTest(t, userKey),
		basePublicKeyForTest(t, cosignerKey),
		basePublicKeyForTest(t, lightningServerKey),
	)
}

// fundingTx returns a tx paying amount to the output's address on its first
// output.
func fundingTx(
	t *testing.T,
	output interface {
		Address() (*WalletAddress, error)
	},
	amount int64,
) *wire.MsgTx {
	t.Helper()

	address, err := output.Address()
	require.NoError(t, err)
	decoded, err := btcutil.DecodeAddress(address.Address(), &chaincfg.RegressionNetParams)
	require.NoError(t, err)
	pkScript, err := txscript.PayToAddrScript(decoded)
	require.NoError(t, err)

	tx := wire.NewMsgTx(2)
	tx.AddTxIn(&wire.TxIn{Sequence: wire.MaxTxInSequenceNum})
	tx.AddTxOut(wire.NewTxOut(amount, pkScript))
	return tx
}

// spendingTx returns a tx spending prevTx's first output, with the given
// input sequence.
func spendingTx(prevTx *wire.MsgTx, sequence uint32) *wire.MsgTx {
	tx := wire.NewMsgTx(2)
	tx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{Hash: prevTx.TxHash(), Index: 0},
		Sequence:         sequence,
	})
	// Send the funds (minus an arbitrary fee) back to the same script
	tx.AddTxOut(wire.NewTxOut(prevTx.TxOut[0].Value-1_000, prevTx.TxOut[0].PkScript))
	return tx
}
