package addresses_test

import (
	"encoding/json"
	"fmt"
	"strconv"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/addresses"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/test/testbitcoind"
	"github.com/muun/libwallet/platform/test/testsuite"
)

// ToClientDescriptorsTestSuite checks that a user holding only the user and
// cosigner private keys and the lightning server's public key recovers the
// funds on every to_client address (V7, V8 and V9) through bitcoin core
// alone. It needs the regtest-musig node.
type ToClientDescriptorsTestSuite struct {
	testsuite.BaseIntegrationSuite
}

func TestToClientDescriptors_Integration(t *testing.T) {
	testsuite.Run(t, new(ToClientDescriptorsTestSuite))
}

// TestRecoversThroughDescriptors imports the spec's output descriptors into a
// descriptor wallet, funds one address per version and derivation tree and
// has core sweep them through the non-collaborative path once older(N)
// matures. The wallet never holds the lightning server's private key, so
// only that path can satisfy the inputs.
func (s *ToClientDescriptorsTestSuite) TestRecoversThroughDescriptors() {
	// MARK: - Step 1: Set up test data
	const (
		// Same keys as TestCreateAddressV7, V8 and V9.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll

		// Small relative timelock so the UTXOs mature within the test.
		timelock = int64(10)

		v7v8SpendingConditionsExternal = "and_v(v:pk(%[1]s/1/*),and_v(v:pk(%[2]s/1/*),or_d(pk(%[3]s/1/*),older(%[4]d))))"                //nolint:lll
		v7v8SpendingConditionsChange   = "and_v(v:pk(%[1]s/0/*),and_v(v:pk(%[2]s/0/*),or_d(pk(%[3]s/0/*),older(%[4]d))))"                //nolint:lll
		v9SpendingConditionsExternal   = "tr(musig(%[1]s/1/*,%[2]s/1/*,%[3]s/1/*),and_v(v:pk(musig(%[1]s/1/*,%[2]s/1/*)),older(%[4]d)))" //nolint:lll
		v9SpendingConditionsChange     = "tr(musig(%[1]s/0/*,%[2]s/0/*,%[3]s/0/*),and_v(v:pk(musig(%[1]s/0/*,%[2]s/0/*)),older(%[4]d)))" //nolint:lll
	)

	descriptors := []descriptorTemplate{
		{template: "sh(wsh(" + v7v8SpendingConditionsExternal + "))", change: false},
		{template: "sh(wsh(" + v7v8SpendingConditionsChange + "))", change: true},
		{template: "wsh(" + v7v8SpendingConditionsExternal + ")", change: false},
		{template: "wsh(" + v7v8SpendingConditionsChange + ")", change: true},
		{template: v9SpendingConditionsExternal, change: false},
		{template: v9SpendingConditionsChange, change: true},
	}

	addresses.SetSpendDelaysForTest(s.T(), timelock)

	// MARK: - Step 2: Derive the keys
	// Public keys can't do hardened derivation, so the descriptors start at
	// the schema base path.
	user := basePrivateKeyForDescriptors(s.T(), userKey)
	cosigner := basePrivateKeyForDescriptors(s.T(), cosignerKey)
	lightningServer := basePrivateKeyForDescriptors(s.T(), lightningServerKey)

	// MARK: - Step 3: Create a wallet importing one descriptor per version (change + external)
	node := testbitcoind.NewClientFor(s.T(), testbitcoind.RegtestMusig)
	wallet := descriptorWallet{node.CreateDescriptorWallet(
		fmt.Sprintf("to_client_descriptors_%d", time.Now().UnixNano()),
	)}
	for _, descriptor := range descriptors {
		wallet.importDescriptor(fmt.Sprintf(
			descriptor.template,
			user.Base58(), cosigner.Base58(), lightningServer.PublicKey().String(), timelock,
		), descriptor.change)
	}
	wallet.rescan()

	// MARK: - Step 4: Fund one address per version and derivation tree
	balancesBeforeFunding := wallet.balances()
	baseKeys := hd_keys.NewPublicKeyTriple(
		user.PublicKey(), cosigner.PublicKey(), lightningServer.PublicKey(),
	)
	funded := fundToClientAddresses(s.T(), node, baseKeys)

	// Mine past the relative timelock so the UTXOs are spendable through older(N).
	node.GenerateBlocks(int(timelock))
	wallet.rescan()

	// MARK: - Step 5: Validate funding
	wallet.assertFunded(funded, balancesBeforeFunding)

	// MARK: - Step 6: Sweep all funds through the non-collaborative path
	// The wallet lacks the lightning server key, so core spends the timelocked
	// user + cosigner branch of every UTXO.
	balancesBeforeSweep := wallet.balances()
	txID := wallet.sweepWithSequence(node, uint32(timelock))

	// MARK: - Step 7: Validate the sweep
	s.Zero(wallet.balances().total, "the wallet must be empty after the sweep")
	amount, fee := wallet.transactionAmountAndFee(txID)
	s.Equal(balancesBeforeSweep.total, amount+fee, "the sweep must move every satoshi")
}

// descriptorTemplate is an output descriptor with its participant keys and
// timelock left as fmt verbs, flagged as the change or the external tree.
type descriptorTemplate struct {
	template string
	change   bool
}

// fundedAddress is an address the node paid and how much it paid.
type fundedAddress struct {
	address string
	amount  btcutil.Amount
}

// walletBalances is the confirmed and unconfirmed balance of a descriptor
// wallet per address and in total.
type walletBalances struct {
	perAddress map[string]btcutil.Amount
	total      btcutil.Amount
}

// descriptorWallet is a bitcoind wallet loaded with the recovery descriptors.
type descriptorWallet struct {
	*testbitcoind.Client
}

// basePrivateKeyForDescriptors derives a base58 master private key to the
// schema base path, where the descriptors' keys start.
func basePrivateKeyForDescriptors(t *testing.T, base58 string) *hd_keys.HDPrivateKey {
	t.Helper()

	master, err := hd_keys.NewHDPrivateKeyFromString(base58, "m", &chaincfg.RegressionNetParams)
	require.NoError(t, err)
	derived, err := master.DeriveTo(addresses.FixtureBasePath)
	require.NoError(t, err)
	return derived
}

// fundToClientAddresses has node pay one address per to_client version and
// derivation tree, mines the payments and returns them.
func fundToClientAddresses(
	t *testing.T,
	node *testbitcoind.Client,
	baseKeys *hd_keys.PublicKeyTriple,
) []fundedAddress {
	t.Helper()

	derivationPaths := []string{
		addresses.FixtureBasePath + "/change:0/0", addresses.FixtureBasePath + "/external:1/0",
	}

	var funded []fundedAddress
	for _, path := range derivationPaths {
		v7, err := addresses.NewToClientV7(baseKeys, path)
		require.NoError(t, err)
		v8, err := addresses.NewToClientV8(baseKeys, path)
		require.NoError(t, err)
		v9, err := addresses.NewToClientV9(baseKeys, path)
		require.NoError(t, err)

		for _, output := range []interface {
			Address() (*addresses.WalletAddress, error)
		}{v7, v8, v9} {
			walletAddress, err := output.Address()
			require.NoError(t, err)
			address, err := btcutil.DecodeAddress(
				walletAddress.Address(), &chaincfg.RegressionNetParams,
			)
			require.NoError(t, err)
			// A distinct amount per version tells the payments apart.
			amount := btcutil.Amount(walletAddress.Version() * 100_000)
			node.SendToAddress(address, amount.ToBTC())
			funded = append(funded, fundedAddress{address: walletAddress.Address(), amount: amount})
			t.Logf("Funded V%d (%s): %s", walletAddress.Version(), path, walletAddress.Address())
		}
	}
	node.GenerateBlocks(1)

	return funded
}

// importDescriptor imports descriptor as an active range descriptor of the
// change or the external tree. Core wants the checksum appended, which
// getdescriptorinfo computes; its normalized descriptor is not reused because
// it replaces the private keys with public ones.
func (w descriptorWallet) importDescriptor(descriptor string, change bool) {
	w.Helper()

	const maxDerivationIndex = 200

	info := testbitcoind.MustMakeRequest[struct {
		Checksum string `json:"checksum"`
	}](w.Client, "getdescriptorinfo", descriptor)

	results := testbitcoind.MustMakeRequest[[]struct {
		Success bool `json:"success"`
		Error   any  `json:"error"`
	}](w.Client, "importdescriptors", []map[string]any{{
		"desc":      descriptor + "#" + info.Checksum,
		"timestamp": 0,
		"active":    true,
		"internal":  change,
		"range":     [2]int{0, maxDerivationIndex},
	}})
	require.Len(w.T, results, 1)
	require.True(w.T, results[0].Success, "importdescriptors failed: %v", results[0].Error)
}

// rescan rescans the chain from genesis and waits for the wallet to finish.
func (w descriptorWallet) rescan() {
	w.Helper()

	const (
		pollInterval = 500 * time.Millisecond
		maxWait      = 10 * time.Second
	)

	testbitcoind.MustMakeRequest[any](w.Client, "rescanblockchain", 0)
	require.Eventually(w.T, func() bool {
		// scanning is false when idle and a progress object while a scan runs.
		info := testbitcoind.MustMakeRequest[struct {
			Scanning json.RawMessage `json:"scanning"`
		}](w.Client, "getwalletinfo")
		return string(info.Scanning) == "false"
	}, maxWait, pollInterval, "the rescan did not finish")
}

// balances lists the wallet's unspent outputs, confirmed or not, by address.
func (w descriptorWallet) balances() walletBalances {
	w.Helper()

	balances := walletBalances{perAddress: make(map[string]btcutil.Amount)}
	for _, utxo := range w.listUnspent() {
		amount, err := btcutil.NewAmount(utxo.Amount)
		require.NoError(w.T, err)
		balances.perAddress[utxo.Address] += amount
		balances.total += amount
	}
	return balances
}

// assertFunded checks that every funded address gained exactly its payment
// and nothing else changed.
func (w descriptorWallet) assertFunded(funded []fundedAddress, before walletBalances) {
	w.Helper()

	after := w.balances()
	var expectedTotal btcutil.Amount
	for _, payment := range funded {
		expectedTotal += payment.amount
		require.Equal(
			w.T, payment.amount,
			after.perAddress[payment.address]-before.perAddress[payment.address],
			"address %s", payment.address,
		)
	}
	require.Equal(w.T, expectedTotal, after.total-before.total)
}

// sweepWithSequence spends every UTXO of the wallet back to node with the
// given nSequence on each input, so the relative timelock paths can be
// satisfied, and mines it. Returns the sweep's txid.
func (w descriptorWallet) sweepWithSequence(node *testbitcoind.Client, sequence uint32) string {
	w.Helper()

	const fee = btcutil.Amount(10_000)

	type input struct {
		testbitcoind.Outpoint
		Sequence uint32 `json:"sequence"`
	}

	utxos := w.listUnspent()
	require.NotEmpty(w.T, utxos, "nothing to sweep")

	inputs := make([]input, 0, len(utxos))
	var total btcutil.Amount
	for _, utxo := range utxos {
		amount, err := btcutil.NewAmount(utxo.Amount)
		require.NoError(w.T, err)
		total += amount
		inputs = append(inputs, input{Outpoint: utxo.Outpoint, Sequence: sequence})
	}
	// Exactly 8 decimals keep core's amount parser away from float noise.
	outputs := map[string]string{
		node.GetNewAddress("receive").EncodeAddress(): strconv.FormatFloat(
			(total - fee).ToBTC(), 'f', 8, 64,
		),
	}

	rawTx := testbitcoind.MustMakeRequest[string](w.Client, "createrawtransaction", inputs, outputs)
	psbt := testbitcoind.MustMakeRequest[string](w.Client, "converttopsbt", rawTx, true)
	// MuSig2 signing takes two rounds through walletprocesspsbt: nonces, then signatures.
	for range 2 {
		psbt = testbitcoind.MustMakeRequest[struct {
			Psbt string `json:"psbt"`
		}](w.Client, "walletprocesspsbt", psbt).Psbt
	}
	finalized := testbitcoind.MustMakeRequest[struct {
		Hex      string `json:"hex"`
		Complete bool   `json:"complete"`
	}](w.Client, "finalizepsbt", psbt)
	require.True(w.T, finalized.Complete, "core could not finalize the sweep")

	txID := w.MustSendRawTransaction(finalized.Hex)
	node.GenerateBlocks(1)
	return txID
}

// transactionAmountAndFee returns what the wallet sent in txID and the fee it paid.
func (w descriptorWallet) transactionAmountAndFee(txID string) (btcutil.Amount, btcutil.Amount) {
	w.Helper()

	// Core reports sent amounts and fees as negative numbers.
	details := testbitcoind.MustMakeRequest[struct {
		Amount float64 `json:"amount"`
		Fee    float64 `json:"fee"`
	}](w.Client, "gettransaction", txID)
	amount, err := btcutil.NewAmount(-details.Amount)
	require.NoError(w.T, err)
	fee, err := btcutil.NewAmount(-details.Fee)
	require.NoError(w.T, err)
	return amount, fee
}

// listUnspent returns the wallet's unspent outputs, confirmed or not.
func (w descriptorWallet) listUnspent() []testbitcoind.ListUnspentResult {
	w.Helper()

	const maxConfirmations = 9_999_999

	return testbitcoind.MustMakeRequest[[]testbitcoind.ListUnspentResult](
		w.Client, "listunspent", 0, maxConfirmations,
	)
}
