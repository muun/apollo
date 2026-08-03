package scanner

import (
	"fmt"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/rpcclient"

	"github.com/muun/libwallet"
)

// This is the M3-family counterpart of TestKitToBtcCore_Integration (see recovery_test.go). It
// validates that, given only
//
//   - Recovery Code
//   - EKit keys (user + muun)
//   - an in-app generated address
//
// a user can recover the funds held on an M3 address by talking to bitcoin core directly through an
// output descriptor.
//
// The crucial difference with the 2-of-2 schemes (V2/V3/V4/V6) is the spending path. An M3 address
// has two ways to spend:
//
//   - collaborative (3-of-3): user + muun + lightning peer.
//   - non-collaborative (2-of-2 + relative timelock): user + muun + older(N).
//
// A recovering user never has the lightning peer key, so recovery MUST go through the
// non-collaborative path: the descriptor carries the peer's PUBLIC key only, so bitcoin core can
// satisfy the input solely via the timelocked user+muun branch once the UTXO matures.
//
// This test depends on regtest-musig, that container will be up for this test on CI but not on local
// envs. If you want to run it locally use `docker compose up regtest-musig`.
func TestM3KitToBtcCore_Integration(t *testing.T) {
	// MARK: - Step 1: Set up test data
	const (
		// Same EKit fixtures as recovery_test.go.
		encodedUserKey = "Fw11jm3oFyL4EEo8tZHpvApSdQ9DkCspVuxG7ZmH9ziTkfFkfpBg9itmFwwmi5GTekvaEwyghJG2phyBJkW4DkqKNqdZx1DRDCmL3s2PuyhticTA8pgfraQo26kLW9zrKVES2pvfgygHms1y" //nolint:lll
		encodedMuunKey = "FvGKMF7cr7mTTF44ZHohs9M7Fh3L5LuUBnDjqJM8kBxuCnYz28i3cjKLEavim2wviGfH95LVBjuxwipbiTyBzDJWwMrQfTG8hq5X144rDeetHHAyGsXBDiyNFWxwN1u6qfQWH9bcC9TGNp6M" //nolint:lll
		recoveryCode   = "LAWN-AXNA-RQ8K-APEA-JKW5-BT2Y-QH75-DRQM"

		// Small relative timelock so the timelocked UTXOs mature within the test instead of the
		// production ~1-year value.
		nonCollaborativeTimelock = int64(10)

		m3SpendingConditionsExternal = "and_v(v:pk(%s/1/*),and_v(v:pk(%s/1/*),or_d(pk(%s/1/*),older(%d))))"                            //nolint:lll
		m3SpendingConditionsChange   = "and_v(v:pk(%s/0/*),and_v(v:pk(%s/0/*),or_d(pk(%s/0/*),older(%d))))"                            //nolint:lll
		v9SpendingConditionsExternal = "tr(musig(%[1]s/1/*,%[2]s/1/*,%[3]s/1/*),and_v(v:pk(musig(%[1]s/1/*,%[2]s/1/*)),older(%[4]d)))" //nolint:lll
		v9SpendingConditionsChange   = "tr(musig(%[1]s/0/*,%[2]s/0/*,%[3]s/0/*),and_v(v:pk(musig(%[1]s/0/*,%[2]s/0/*)),older(%[4]d)))" //nolint:lll
	)

	walletDescriptors := []m3WalletDescriptor{
		// V7 external (wrapped segwit)
		{template: "sh(wsh(" + m3SpendingConditionsExternal + "))", change: false},
		// V7 change (wrapped segwit)
		{template: "sh(wsh(" + m3SpendingConditionsChange + "))", change: true},
		// V8 external (native segwit)
		{template: "wsh(" + m3SpendingConditionsExternal + ")", change: false},
		// V8 change (native segwit)
		{template: "wsh(" + m3SpendingConditionsChange + ")", change: true},
		// V9 external (taproot)
		{template: v9SpendingConditionsExternal, change: false},
		// V9 change (taproot)
		{template: v9SpendingConditionsChange, change: true},
	}

	// MARK: - Step 2: Decrypt keys
	userKey, muunKey := decryptMuunKeys(t, encodedUserKey, encodedMuunKey, recoveryCode, nil)

	// The lightning peer is a third party. For recovery we only ever need its PUBLIC key.
	peerKey := newPeerKey(t)

	// MARK: - Step 3: Create wallet importing one descriptor per M3 scheme (change + external)
	daemonRPC := getBitcoindRpcClient(t, "")
	userWalletRPC := loadM3Wallet(
		t,
		userKey,
		muunKey,
		peerKey,
		nonCollaborativeTimelock,
		walletDescriptors,
	)

	// MARK: - Step 4: Fund one address per M3 scheme and derivation tree.
	userWalletStateBeforeFunding := getWalletState(t, userWalletRPC)

	fundedAddresses := fundM3Addresses(
		t,
		userKey,
		muunKey,
		peerKey,
		daemonRPC,
		nonCollaborativeTimelock,
	)

	// Mine past the relative timelock so the UTXOs are spendable through older(N).
	for range nonCollaborativeTimelock {
		generateBlock(t, daemonRPC)
	}
	rescanTheBlockchain(t, userWalletRPC)

	// MARK: - Step 5: Validate funding
	checkFundsAdded(t, userWalletRPC, fundedAddresses, userWalletStateBeforeFunding)

	userWalletStateBeforeSpendAllFunds := getWalletState(t, userWalletRPC)

	// MARK: - Step 6: Spend all funds via the non-collaborative path
	// The wallet lacks the peer key, so core spends the timelocked user+muun branch of every UTXO.
	txID := spendAllFundsWithTimelockSequence(t, userWalletRPC, daemonRPC, nonCollaborativeTimelock)

	// MARK: - Step 7: Validate transaction
	checkUserBalanceIsZero(t, userWalletRPC)
	checkTxAmountIsConsistentWithUserBalanceBeforeSpend(
		t,
		userWalletRPC,
		txID,
		userWalletStateBeforeSpendAllFunds,
	)
}

// newPeerKey builds a deterministic lightning-peer key. Only its public key is ever used (it goes
// into the descriptor as an xpub), since recovery never spends the collaborative branch.
func newPeerKey(t *testing.T) *libwallet.HDPrivateKey {
	seed := make([]byte, 32)
	for i := range seed {
		seed[i] = 0x07
	}
	peerKey, err := libwallet.NewMasterHDPrivateKeyFromBytes(
		seed, make([]byte, 32), libwallet.Regtest(),
	)
	if err != nil {
		t.Fatalf("Failed to build peer key: %v", err)
	}
	return peerKey
}

type m3WalletDescriptor struct {
	template string
	change   bool
}

// loadM3Wallet creates a fresh descriptor wallet and imports every M3 descriptor
// (user+muun xprivs, peer xpub).
func loadM3Wallet(
	t *testing.T,
	userKey, muunKey, peerKey *libwallet.HDPrivateKey,
	blocksForExpiration int64,
	walletDescriptors []m3WalletDescriptor,
) *rpcclient.Client {
	walletName := fmt.Sprintf("recovery_m3_%d", time.Now().UnixNano())
	walletRPC := createDescriptorWallet(t, walletName)

	for _, desc := range walletDescriptors {
		descriptor := fmt.Sprintf(
			desc.template,
			userKey.String(),
			muunKey.String(),
			peerKey.PublicKey().String(),
			blocksForExpiration,
		)

		// The btcCore protocol requires the checksum in importDescriptor to be already added.
		descriptorWithChecksum := addDescriptorChecksum(t, walletRPC, descriptor)
		importDescriptor(t, walletRPC, descriptorWithChecksum, desc.change)
	}

	rescanTheBlockchain(t, walletRPC)

	return walletRPC
}

// fundM3Addresses funds one address per scheme version and derivation tree and returns
// what it funded.
func fundM3Addresses(
	t *testing.T,
	userKey, muunKey, peerKey *libwallet.HDPrivateKey,
	daemonRPC *rpcclient.Client,
	blocksForExpiration int64,
) []AddressWithBalance {
	addresses := generateM3Addresses(t, userKey, muunKey, peerKey, blocksForExpiration)
	var fundedAddresses []AddressWithBalance

	for _, addr := range addresses {
		amount := btcutil.Amount(addr.Version() * 100000)
		fundAddress(t, daemonRPC, addr.Address(), amount)
		fundedAddresses = append(fundedAddresses, AddressWithBalance{
			address: addr.Address(),
			balance: amount,
		})
	}

	generateBlock(t, daemonRPC)

	return fundedAddresses
}

// generateM3Addresses returns one libwallet-generated address per scheme version AND derivation
// tree.
func generateM3Addresses(
	t *testing.T,
	userKey, muunKey, peerKey *libwallet.HDPrivateKey,
	blocksForExpiration int64,
) []libwallet.MuunAddress {
	derivationPaths := []string{"m/0/0", "m/1/0"}

	var addresses []libwallet.MuunAddress
	for _, path := range derivationPaths {
		derivedUserKey, err := userKey.PublicKey().DeriveTo(path)
		if err != nil {
			t.Fatalf("Failed to derive user key at %s: %v", path, err)
		}
		derivedMuunKey, err := muunKey.PublicKey().DeriveTo(path)
		if err != nil {
			t.Fatalf("Failed to derive muun key at %s: %v", path, err)
		}
		derivedPeerKey, err := peerKey.PublicKey().DeriveTo(path)
		if err != nil {
			t.Fatalf("Failed to derive peer key at %s: %v", path, err)
		}

		addrV7, err := libwallet.CreateAddressV7(
			derivedUserKey, derivedMuunKey, derivedPeerKey, blocksForExpiration,
		)
		if err != nil {
			t.Fatalf("Failed to create V7 address at %s: %v", path, err)
		}
		addrV8, err := libwallet.CreateAddressV8(
			derivedUserKey, derivedMuunKey, derivedPeerKey, blocksForExpiration,
		)
		if err != nil {
			t.Fatalf("Failed to create V8 address at %s: %v", path, err)
		}
		addrV9, err := libwallet.CreateAddressV9(
			derivedUserKey, derivedMuunKey, derivedPeerKey, blocksForExpiration,
		)
		if err != nil {
			t.Fatalf("Failed to create V9 address at %s: %v", path, err)
		}

		for _, addr := range []libwallet.MuunAddress{addrV7, addrV8, addrV9} {
			addresses = append(addresses, addr)
			t.Logf("Generated V%d (%s): %s", addr.Version(), addr.DerivationPath(), addr.Address())
		}
	}
	return addresses
}
