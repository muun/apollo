package scanner

import (
	"encoding/json"
	"fmt"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/rpcclient"
	"strings"
	"testing"
	"time"
)

type AddressWithBalance struct {
	address string
	balance btcutil.Amount
}

type WalletState struct {
	addressBalances map[string]btcutil.Amount
	totalBalance    btcutil.Amount
}

func (ws WalletState) getBalance(address string) btcutil.Amount {
	return ws.addressBalances[address]
}

func getBitcoindRpcClient(t *testing.T, wallet string) *rpcclient.Client {
	const (
		rpcHost     = "localhost:38443"
		rpcUser     = "user"
		rpcPassword = "123"
	)

	host := rpcHost + "/"
	if wallet != "" && wallet != "daemon" {
		host += "wallet/" + wallet
	} else if wallet == "" {
		// Use the default wallet explicitly
		host += "wallet/"
	}

	client, err := rpcclient.New(&rpcclient.ConnConfig{
		Host:         host,
		User:         rpcUser,
		Pass:         rpcPassword,
		DisableTLS:   true,
		HTTPPostMode: true,
	}, nil)

	if err != nil {
		t.Fatal(err)
	}
	return client
}

func mustMarshal(v interface{}) json.RawMessage {
	data, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return json.RawMessage(data)
}

func createDescriptorWallet(t *testing.T, walletName string) *rpcclient.Client {
	rpc := getBitcoindRpcClient(t, "daemon")

	createParams := []json.RawMessage{
		mustMarshal(walletName), mustMarshal(false), mustMarshal(false),
		mustMarshal(""), mustMarshal(false), mustMarshal(true),
	}

	_, err := rpc.RawRequest("createwallet", createParams)
	if err != nil && strings.Contains(err.Error(), "already exists") {
		rpc.RawRequest("loadwallet", []json.RawMessage{mustMarshal(walletName)})
	}

	return getBitcoindRpcClient(t, walletName)
}

func rescanTheBlockchain(t *testing.T, rpc *rpcclient.Client) {
	const rescanStartBlock = 0

	_, err := rpc.RawRequest(
		"rescanblockchain",
		[]json.RawMessage{mustMarshal(rescanStartBlock)},
	)
	if err != nil {
		t.Fatalf("Failed to rescan blockchain: %v", err)
	}

	const pollInterval = 500 * time.Millisecond
	const maxWait = 10 * time.Second
	deadline := time.Now().Add(maxWait)

	for time.Now().Before(deadline) {
		result, err := rpc.RawRequest("getwalletinfo", nil)
		if err != nil {
			t.Fatalf("Failed to get wallet info: %v", err)
		}

		var info map[string]interface{}
		if err := json.Unmarshal(result, &info); err != nil {
			t.Fatalf("Failed to unmarshal wallet info: %v", err)
		}

		scanning, ok := info["scanning"].(bool)
		if !ok || !scanning {
			t.Logf("Rescan completed")
			return
		}

		time.Sleep(pollInterval)
	}

	t.Fatalf("Rescan did not complete within %v", maxWait)
}

func getWalletState(t *testing.T, walletRpc *rpcclient.Client) WalletState {
	const maxConfirmations = 9999999

	unspentResult, err := walletRpc.RawRequest(
		"listunspent",
		[]json.RawMessage{mustMarshal(0), mustMarshal(maxConfirmations)},
	)
	if err != nil {
		t.Fatalf("Failed to list unspent outputs: %v", err)
	}

	var unspents []map[string]interface{}
	if err := json.Unmarshal(unspentResult, &unspents); err != nil {
		t.Fatalf("Failed to unmarshal unspent outputs: %v", err)
	}

	// Use a map to aggregate balances by address
	addressBalances := make(map[string]btcutil.Amount)
	var total btcutil.Amount
	for _, utxo := range unspents {
		if addr, ok := utxo["address"].(string); ok {
			if amountBTC, ok := utxo["amount"].(float64); ok {
				amount, err := btcutil.NewAmount(amountBTC)
				if err != nil {
					t.Fatalf("Failed to convert amount: %v", err)
				}
				addressBalances[addr] += amount
				total += amount
			}
		}
	}

	return WalletState{
		addressBalances: addressBalances,
		totalBalance:    total,
	}
}

func fundAddress(
	t *testing.T,
	senderRpc *rpcclient.Client,
	address string,
	amount btcutil.Amount,
) {
	decodedAddress, err := btcutil.DecodeAddress(address, &chaincfg.RegressionNetParams)
	if err != nil {
		t.Fatalf("Failed to decode address %s: %v", address, err)
	}
	txid, err := senderRpc.SendToAddress(decodedAddress, amount)
	if err != nil {
		t.Fatalf("Failed to send %d to address %s: %v", amount, address, err)
	}
	t.Logf("Funded %s with %d - txid: %s", address, amount, txid)
}

func generateBlock(t *testing.T, rpc *rpcclient.Client) {
	// Confirm transactions
	fundingAddr, err := rpc.GetNewAddress("mining")
	if err != nil {
		t.Fatalf("Failed to get funding address: %v", err)
	}

	const confirmationBlock = 1
	_, err = rpc.GenerateToAddress(confirmationBlock, fundingAddr, nil)
	if err != nil {
		t.Fatalf("Failed to generate confirmation block: %v", err)
	}
}

func checkFundsAdded(
	t *testing.T,
	userWalletRpc *rpcclient.Client,
	fundedAddresses []AddressWithBalance,
	walletStateBeforeFunding WalletState,
) {
	// To get how much was added to an address we are comparing with the address balance
	// before funding.
	finalState := getWalletState(t, userWalletRpc)

	// Validate funding and calculate expected total
	var expectedTotal btcutil.Amount
	for _, funded := range fundedAddresses {
		expectedTotal += funded.balance

		initialBalance := walletStateBeforeFunding.getBalance(funded.address)
		finalBalance := finalState.getBalance(funded.address)
		actualIncrease := finalBalance - initialBalance

		if actualIncrease != funded.balance {
			t.Fatalf(
				"Address %s: expected +%d, got +%d",
				funded.address,
				funded.balance,
				actualIncrease,
			)
		}
	}

	// Validate total balance increase
	totalIncrease := finalState.totalBalance - walletStateBeforeFunding.totalBalance
	if totalIncrease != expectedTotal {
		t.Fatalf("Total: expected +%d, got +%d", expectedTotal, totalIncrease)
	}

	t.Logf("✅ Funds validation passed: +%d", totalIncrease)
}

func spendAllFundsFromUserWallet(
	t *testing.T,
	userWalletRpc,
	daemonRpc *rpcclient.Client,
) string {
	balancesBefore := getWalletState(t, userWalletRpc)

	if balancesBefore.totalBalance == 0 {
		t.Fatalf("No funds to spend")
	}

	destAddr, err := daemonRpc.GetNewAddress("receive")
	if err != nil {
		t.Fatalf("Failed to get destination address: %v", err)
	}

	// Use PSBT for spending (required for MuSig2)
	psbt := createFundedPSBT(t, userWalletRpc, destAddr.EncodeAddress(), balancesBefore.totalBalance)
	signedPsbt := signPSBT(t, userWalletRpc, psbt)
	txHex := finalizePSBT(t, userWalletRpc, signedPsbt)
	txid := broadcastTransaction(t, userWalletRpc, txHex)

	t.Logf("Sent %d sats via PSBT (txid: %s)", balancesBefore.totalBalance, txid)
	generateBlock(t, daemonRpc)

	return txid
}

func getTxAmountAndFee(
	t *testing.T,
	walletRPC *rpcclient.Client,
	txid string,
) (btcutil.Amount, btcutil.Amount) {
	// Validate actualTransfer by checking the transaction details
	txResult, err := walletRPC.RawRequest(
		"gettransaction",
		[]json.RawMessage{mustMarshal(txid)},
	)
	if err != nil {
		t.Fatalf("Failed to get transaction details: %v", err)
	}

	var txDetails map[string]interface{}
	if err := json.Unmarshal(txResult, &txDetails); err != nil {
		t.Fatalf("Failed to unmarshal transaction details: %v", err)
	}

	// Get the amount sent in the transaction (negative value for sent funds) and fee
	txAmountBTC := -txDetails["amount"].(float64)
	txFeeBTC := -txDetails["fee"].(float64)

	txAmount, err := btcutil.NewAmount(txAmountBTC)
	if err != nil {
		t.Fatalf("Failed to convert transaction amount: %v", err)
	}

	txFee, err := btcutil.NewAmount(txFeeBTC)
	if err != nil {
		t.Fatalf("Failed to convert transaction fee: %v", err)
	}

	return txAmount, txFee
}

func createFundedPSBT(t *testing.T, rpc *rpcclient.Client, destAddr string, amount btcutil.Amount) string {
	amountStr := fmt.Sprintf("%.8f", amount.ToBTC())
	result, err := rpc.RawRequest(
		"walletcreatefundedpsbt",
		[]json.RawMessage{
			mustMarshal([]interface{}{}),                                 // inputs (auto-select)
			mustMarshal([]map[string]interface{}{{destAddr: amountStr}}), // outputs
			mustMarshal(0), // locktime
			mustMarshal(map[string]interface{}{"subtractFeeFromOutputs": []int{0}}), // options
		},
	)
	if err != nil {
		t.Fatalf("Failed to create funded PSBT: %v", err)
	}

	return extractPSBTFromResponse(t, result, "PSBT")
}

func signPSBT(t *testing.T, rpc *rpcclient.Client, psbt string) string {
	// First round
	firstRound := processPSBT(t, rpc, psbt)
	// MuSig2 requires two rounds
	return processPSBT(t, rpc, firstRound)
}

func processPSBT(t *testing.T, rpc *rpcclient.Client, psbt string) string {
	result, err := rpc.RawRequest(
		"walletprocesspsbt",
		[]json.RawMessage{
			mustMarshal(psbt), // psbt
		},
	)
	if err != nil {
		t.Fatalf("Failed to process PSBT: %v", err)
	}

	return extractPSBTFromResponse(t, result, "processed PSBT")
}

func finalizePSBT(t *testing.T, rpc *rpcclient.Client, psbt string) string {
	result, err := rpc.RawRequest(
		"finalizepsbt",
		[]json.RawMessage{
			mustMarshal(psbt), // psbt
		},
	)
	if err != nil {
		t.Fatalf("Failed to finalize PSBT: %v", err)
	}

	var response map[string]interface{}
	if err := json.Unmarshal(result, &response); err != nil {
		t.Fatalf("Failed to unmarshal finalized PSBT: %v", err)
	}

	complete, _ := response["complete"].(bool)
	if !complete {
		t.Fatalf("PSBT could not be finalized completely")
	}

	hex, ok := response["hex"].(string)
	if !ok || hex == "" {
		t.Fatalf("Transaction hex not found in finalized PSBT")
	}

	return hex
}

func broadcastTransaction(t *testing.T, rpc *rpcclient.Client, txHex string) string {
	result, err := rpc.RawRequest(
		"sendrawtransaction",
		[]json.RawMessage{mustMarshal(txHex)}, // hexstring
	)
	if err != nil {
		t.Fatalf("Failed to send raw transaction: %v", err)
	}

	var txid string
	if err := json.Unmarshal(result, &txid); err != nil {
		t.Fatalf("Failed to unmarshal transaction ID: %v", err)
	}

	return txid
}

func extractPSBTFromResponse(t *testing.T, result json.RawMessage, description string) string {
	var response map[string]interface{}
	if err := json.Unmarshal(result, &response); err != nil {
		t.Fatalf("Failed to unmarshal %s response: %v", description, err)
	}

	psbt, ok := response["psbt"].(string)
	if !ok {
		t.Fatalf("%s not found in response", description)
	}

	return psbt
}
