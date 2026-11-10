package scanner

import (
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/rpcclient"

	libwallet "github.com/muun/libwallet/legacy"
)

/*
*
This test aims to validate that given
- Recovery Code
- EKit keys
- In app generated address

We are able to recover the user funds talking to bitcoin core directly and using
output descriptors.
The idea is to test:
- Given a kit and an address generated manually in app, the sats can be recovered.
- Given every single type of address we can generate with libwallet,
there are output descriptors that bitcoin core can understand and use to move the user sats.

This test depends on regtest-musig, that container will be up
for this test on CI but not on local envs. if you want to run
it locally use `docker compose up regtest-musig.`
*/
func TestKitToBtcCore_Integration(t *testing.T) {
	// MARK: - Step 1: Set up test data
	const (
		// Test keys and recovery data
		encodedUserKey        = "Fw11jm3oFyL4EEo8tZHpvApSdQ9DkCspVuxG7ZmH9ziTkfFkfpBg9itmFwwmi5GTekvaEwyghJG2phyBJkW4DkqKNqdZx1DRDCmL3s2PuyhticTA8pgfraQo26kLW9zrKVES2pvfgygHms1y" //nolint:lll
		encodedCosignerKey    = "FvGKMF7cr7mTTF44ZHohs9M7Fh3L5LuUBnDjqJM8kBxuCnYz28i3cjKLEavim2wviGfH95LVBjuxwipbiTyBzDJWwMrQfTG8hq5X144rDeetHHAyGsXBDiyNFWxwN1u6qfQWH9bcC9TGNp6M" //nolint:lll
		recoveryCode          = "LAWN-AXNA-RQ8K-APEA-JKW5-BT2Y-QH75-DRQM"
		inAppGeneratedAddress = "2N1PtMVLGB2cV3Afn4HwouPZJK1tkFE7GEi"

		// 2-of-2 multisig spending conditions
		m2SpendingConditionsExternal = "multi(2,%s/1/*,%s/1h/1h/1/*)"
		m2SpendingConditionsInternal = "multi(2,%s/0/*,%s/1h/1h/0/*)"
	)

	walletDescriptors := []struct {
		template string
		internal bool
	}{
		{"sh(" + m2SpendingConditionsExternal + ")", false},      // V2
		{"sh(wsh(" + m2SpendingConditionsExternal + "))", false}, // V3
		{"wsh(" + m2SpendingConditionsExternal + ")", false},     // V4
		{"tr(musig(%s/1/*,%s/1h/1h/1/*))", false},                // V6 MuSig2 external
		{"sh(" + m2SpendingConditionsInternal + ")", true},       // V2
		{"sh(wsh(" + m2SpendingConditionsInternal + "))", true},  // V3
		{"wsh(" + m2SpendingConditionsInternal + ")", true},      // V4
		{"tr(musig(%s/0/*,%s/1h/1h/0/*))", true},                 // V6 MuSig2 internal
	}

	// MARK: - Step 2: Decrypt keys
	// Convert encrypted keys to master keys using the recovery code and label the user key
	// accordingly.
	userPath := "m/1'/1'"
	userKey, cosignerKey := decryptEmergencyKitKeys(
		t,
		encodedUserKey,
		encodedCosignerKey,
		recoveryCode,
		&userPath,
	)

	// MARK: - Step 3: Create wallets
	// Use default wallet for funding operations (empty string uses wallet/ path)
	daemonRPC := getBitcoindRPCClient(
		t,
		"",
	)
	userWalletRPC := loadUserWallet(
		t,
		userKey,
		cosignerKey,
		walletDescriptors,
	)

	// MARK: - Step 4: Fund addresses
	// Generate and fund one address per version

	// We might have a previous state so we must consider it.
	userWalletStateBeforeFunding := getWalletState(t, userWalletRPC)

	fundedAddresses := fundOneAddressPerVersion(
		t,
		userKey,
		cosignerKey,
		daemonRPC,
		inAppGeneratedAddress,
		userWalletRPC,
	)

	// MARK: - Step 5: Validate funding
	// Check that funds were properly added to addresses
	checkFundsAdded(t, userWalletRPC, fundedAddresses, userWalletStateBeforeFunding)

	userWalletStateBeforeSpendAllFunds := getWalletState(t, userWalletRPC)

	// MARK: - Step 6: Spend all funds
	// Transfer all funds from user wallet back to daemon
	txID := spendAllFundsFromUserWallet(t, userWalletRPC, daemonRPC)

	// MARK: - Step 7: Validate transaction
	// Verify transaction amounts and fees match expected values
	checkUserBalanceIsZero(t, userWalletRPC)
	checkTxAmountIsConsistentWithUserBalanceBeforeSpend(
		t,
		userWalletRPC,
		txID,
		userWalletStateBeforeSpendAllFunds,
	)
}

func checkTxAmountIsConsistentWithUserBalanceBeforeSpend(
	t *testing.T,
	userWalletRPC *rpcclient.Client,
	txID string,
	userWalletStateBeforeSpendAllFunds WalletState,
) {

	txAmount, txFee := getTxAmountAndFee(t, userWalletRPC, txID)
	walletBalanceBeforeSpendAllFunds := userWalletStateBeforeSpendAllFunds.totalBalance

	// Since we use subtractfeefromamount=true, the transaction amount + fee should equal wallet
	// balance
	if txAmount+txFee != walletBalanceBeforeSpendAllFunds {
		t.Fatalf(
			"Transaction validation failed: "+
				"txAmount (%d) + txFee (%d) = %d != walletBalance (%d)",
			txAmount, txFee, txAmount+txFee, walletBalanceBeforeSpendAllFunds,
		)
	}

	t.Logf(
		"✅ Transaction validation passed: %d sent with %d fee (txID: %s)",
		txAmount, txFee, txID,
	)
	t.Logf(
		"✅ Wallet emptied successfully: %d transferred",
		walletBalanceBeforeSpendAllFunds,
	)
}

func checkUserBalanceIsZero(
	t *testing.T,
	userWalletRPC *rpcclient.Client,
) {
	userWalletStateAfterSpendingAllFunds := getWalletState(t, userWalletRPC)

	if userWalletStateAfterSpendingAllFunds.totalBalance != 0 {
		t.Fatalf(
			"User wallet should be empty, has: %d",
			userWalletStateAfterSpendingAllFunds.totalBalance,
		)
	}
}

func loadUserWallet(
	t *testing.T,
	userKey,
	cosignerKey *libwallet.HDPrivateKey,
	walletDescriptors []struct {
		template string
		internal bool
	},
) *rpcclient.Client {
	walletName := fmt.Sprintf("recovery_%d", time.Now().UnixNano())
	walletRPC := createDescriptorWallet(
		t,
		walletName,
	)

	for _, desc := range walletDescriptors {
		descriptor := fmt.Sprintf(
			desc.template,
			userKey.Base58(),
			cosignerKey.Base58(),
		)

		// The btcCore protocol requires the checksum in the importDescriptor function to be already
		// added.
		descriptorWithChecksum := addDescriptorChecksum(t, walletRPC, descriptor)
		importDescriptor(t, walletRPC, descriptorWithChecksum, desc.internal)
	}

	rescanTheBlockchain(t, walletRPC)

	return walletRPC
}

func addDescriptorChecksum(
	t *testing.T,
	walletRPC *rpcclient.Client,
	descriptor string,
) string {
	result, err := walletRPC.RawRequest(
		"getdescriptorinfo",
		[]json.RawMessage{mustMarshal(descriptor)},
	)
	if err != nil {
		t.Fatalf("Failed to get descriptor info for %s: %v", descriptor, err)
	}

	var info map[string]any
	if err := json.Unmarshal(result, &info); err != nil {
		t.Fatalf("Failed to unmarshal descriptor info: %v", err)
	}

	// Careful here, getdescriptorinfo is responding with the complete descriptor plus the
	// checksum. DO NOT use it as the descriptor returned uses the xpub instead of the xpriv,
	// extract the checksum instead.
	if checksum, ok := info["checksum"].(string); ok {
		return fmt.Sprintf("%s#%s", descriptor, checksum)
	}
	t.Fatalf("Could not find checksum in descriptor info for: %s", descriptor)
	return ""
}

func importDescriptor(
	t *testing.T,
	walletRPC *rpcclient.Client,
	descriptorWithChecksum string,
	internal bool,
) {
	const maxDerivationIndex = 200

	importDesc := map[string]any{
		"desc": descriptorWithChecksum, "timestamp": 0, "active": true,
		"internal": internal, "range": [2]int{0, maxDerivationIndex},
	}

	result, err := walletRPC.RawRequest(
		"importdescriptors",
		[]json.RawMessage{
			mustMarshal([]any{importDesc}),
		},
	)
	if err != nil {
		t.Fatalf("Failed to import descriptor %s: %v", descriptorWithChecksum, err)
	}

	var results []map[string]any
	if err := json.Unmarshal(result, &results); err != nil {
		t.Fatalf("Failed to unmarshal import results: %v", err)
	}

	if len(results) > 0 {
		if success, ok := results[0]["success"].(bool); !ok || !success {
			t.Fatalf(
				"Import failed for descriptor %s: %v",
				descriptorWithChecksum,
				results[0],
			)
		}
	} else {
		t.Fatalf("No results returned from descriptor import for: %s", descriptorWithChecksum)
	}
}

func fundOneAddressPerVersion(
	t *testing.T,
	userKey *libwallet.HDPrivateKey,
	cosignerKey *libwallet.HDPrivateKey,
	daemonRPC *rpcclient.Client,
	inAppGeneratedAddress string,
	userWalletRPC *rpcclient.Client,
) []AddressWithBalance {
	addressesByVersion := generateOneAddressPerVersion(t, userKey, cosignerKey)
	// Track what we're going to fund (only recoverable addresses)
	var fundedAddresses []AddressWithBalance

	// Fund in-app generated address
	const inAppFundingAmount = btcutil.Amount(100000)
	fundAddress(t, daemonRPC, inAppGeneratedAddress, inAppFundingAmount)
	fundedAddresses = append(
		fundedAddresses,
		AddressWithBalance{
			address: inAppGeneratedAddress,
			balance: inAppFundingAmount,
		},
	)

	// Exclude versions that don't have descriptor support yet.
	excludedVersions := map[int]bool{
		5: true, // V5: MuSig with Muun-specific variant
	}

	for version, addr := range addressesByVersion {
		if excludedVersions[version] {
			t.Logf("Skipping V%d (not yet supported for recovery)", version)
			continue
		}

		amount := btcutil.Amount(version * 100000)
		fundAddress(t, daemonRPC, addr.Address(), amount)
		fundedAddresses = append(
			fundedAddresses,
			AddressWithBalance{
				address: addr.Address(),
				balance: amount,
			},
		)
	}

	generateBlock(t, daemonRPC)

	rescanTheBlockchain(t, userWalletRPC)

	return fundedAddresses
}

func generateOneAddressPerVersion(
	t *testing.T,
	userKey *libwallet.HDPrivateKey,
	cosignerKey *libwallet.HDPrivateKey,
) map[int]libwallet.MuunAddress {
	// Address generator requires both addresses to be in the same path
	derivedCosignerKey, err := cosignerKey.DeriveTo("m/1'/1'")
	if err != nil {
		t.Fatalf("Failed to derive key2: %v", err)
	}

	// Generate addresses
	generator := NewAddressGenerator(
		userKey.PublicKey().ToModel(),
		derivedCosignerKey.PublicKey().ToModel(),
		false,
	)
	addressesByVersion := make(map[int]libwallet.MuunAddress)

	for addr := range generator.Stream(0) {
		version := addr.Version()
		if _, exists := addressesByVersion[version]; !exists {
			addressesByVersion[version] = addr
			t.Logf("Generated V%d: %s", version, addr.Address())
		}
	}
	return addressesByVersion
}

func decryptEmergencyKitKeys(
	t *testing.T,
	userKey string,
	cosignerKey string,
	recoveryCode string,
	userPath *string,
) (*libwallet.HDPrivateKey, *libwallet.HDPrivateKey) {
	// Decode encrypted keys
	userEncryptedKey, err := libwallet.DecodeEncryptedPrivateKey(userKey)
	if err != nil {
		t.Fatalf("Failed to decode user key: %v", err)
	}
	cosignerEncryptedKey, err := libwallet.DecodeEncryptedPrivateKey(cosignerKey)
	if err != nil {
		t.Fatalf("Failed to decode cosigner key: %v", err)
	}

	// Create decryption key from recovery code
	decryptionKey, err := libwallet.RecoveryCodeToKey(recoveryCode, cosignerEncryptedKey.Salt)
	if err != nil {
		t.Fatalf("Failed to process recovery code: %v", err)
	}

	// Decrypt both keys
	decryptedUserKey, err := decryptionKey.DecryptKey(userEncryptedKey, libwallet.Regtest())
	if err != nil {
		t.Fatalf("Failed to decrypt user key: %v", err)
	}
	decryptedCosignerKey, err := decryptionKey.DecryptKey(cosignerEncryptedKey, libwallet.Regtest())
	if err != nil {
		t.Fatalf("Failed to decrypt cosigner key: %v", err)
	}

	// Relabel the user key's path when the caller needs it.
	if userPath != nil {
		decryptedUserKey.Key.Path = *userPath
	}
	return decryptedUserKey.Key, decryptedCosignerKey.Key
}
