package scanner

import (
	"fmt"
	"time"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/btcsuitew/btcutilw"
	"github.com/muun/libwallet/btcsuitew/txscriptw"
	"github.com/muun/libwallet/electrum"
)

// scanTask encapsulates a parallelizable Scanner unit of work.
type scanTask struct {
	servers     *electrum.ServerProvider
	client      *electrum.Client
	addresses   []libwallet.MuunAddress
	timeout     time.Duration
	exit        chan struct{}
	chainParams *chaincfg.Params
}

// scanTaskResult contains a summary of the execution of a task.
type scanTaskResult struct {
	Task  *scanTask
	Utxos []*Utxo
	Err   error
}

// Execute obtains the Utxo set for the Task address, implementing a retry strategy.
func (t *scanTask) Execute() *scanTaskResult {
	results := make(chan *scanTaskResult)
	timeout := time.After(t.timeout)

	// Keep the last error around, in case we reach the timeout and want to know the reason:
	var lastError error

	for {
		// Attempt to run the task:
		go t.tryExecuteAsync(results)

		// Wait until a result is sent, the timeout is reached or the task canceled, capturing errors
		// errors along the way:
		select {
		case <-t.exit:
			return t.exitResult() // stop retrying when we get the done signal

		case result := <-results:
			if result.Err == nil {
				return result // we're done! nice work everyone.
			}

			lastError = result.Err // keep retrying when an attempt fails

		case <-timeout:
			return t.errorResult(fmt.Errorf("task timed out. Last error: %w", lastError)) // stop on timeout
		}
	}
}

func (t *scanTask) tryExecuteAsync(results chan *scanTaskResult) {
	// Errors will almost certainly arise from Electrum server failures, which are extremely
	// common. Unreachable IPs, dropped connections, sudden EOFs, etc. We'll run this task, assuming
	// the servers are at fault when something fails, disconnecting and cycling them as we retry.
	result := t.tryExecute()

	if result.Err != nil {
		t.client.Disconnect()
	}

	results <- result
}

func (t *scanTask) tryExecute() *scanTaskResult {
	// If our client is not connected, make an attempt to connect to a server:
	if !t.client.IsConnected() {
		err := t.client.Connect(t.servers.NextServer())

		if err != nil {
			return t.errorResult(err)
		}
	}

	// Prepare the output scripts for all given addresses:
	outputScripts, err := getOutputScripts(t.addresses, t.chainParams)
	if err != nil {
		return t.errorResult(err)
	}

	// Prepare the index hashes that Electrum requires to list outputs:
	indexHashes, err := getIndexHashes(outputScripts)
	if err != nil {
		return t.errorResult(err)
	}

	// Call Electrum to get the unspent output list, grouped by index for each address:
	var unspentRefGroups [][]electrum.UnspentRef

	if t.client.SupportsBatching() {
		unspentRefGroups, err = t.listUnspentWithBatching(indexHashes)
	} else {
		unspentRefGroups, err = t.listUnspentWithoutBatching(indexHashes)
	}

	if err != nil {
		return t.errorResult(err)
	}

	// Compile the results into a list of `Utxos`:
	var utxos []*Utxo

	for i, unspentRefGroup := range unspentRefGroups {
		for _, unspentRef := range unspentRefGroup {
			newUtxo := &Utxo{
				TxID:        unspentRef.TxHash,
				OutputIndex: unspentRef.TxPos,
				Amount:      unspentRef.Value,
				Script:      outputScripts[i],
				Address:     t.addresses[i],
			}

			utxos = append(utxos, newUtxo)
		}
	}

	return t.successResult(utxos)
}

func (t *scanTask) listUnspentWithBatching(indexHashes []string) ([][]electrum.UnspentRef, error) {
	unspentRefGroups, err := t.client.ListUnspentBatch(indexHashes)
	if err != nil {
		return nil, fmt.Errorf("listing with batching failed: %w", err)
	}

	return unspentRefGroups, nil
}

func (t *scanTask) listUnspentWithoutBatching(indexHashes []string) ([][]electrum.UnspentRef, error) {
	var unspentRefGroups [][]electrum.UnspentRef

	for _, indexHash := range indexHashes {
		newGroup, err := t.client.ListUnspent(indexHash)
		if err != nil {
			return nil, fmt.Errorf("listing without batching failed: %w", err)
		}

		unspentRefGroups = append(unspentRefGroups, newGroup)
	}

	return unspentRefGroups, nil
}

func (t *scanTask) errorResult(err error) *scanTaskResult {
	return &scanTaskResult{Task: t, Err: err}
}

func (t *scanTask) successResult(utxos []*Utxo) *scanTaskResult {
	return &scanTaskResult{Task: t, Utxos: utxos}
}

func (t *scanTask) exitResult() *scanTaskResult {
	return &scanTaskResult{Task: t}
}

// getIndexHashes calculates all the Electrum index hashes for a list of output scripts.
func getIndexHashes(outputScripts [][]byte) ([]string, error) {
	indexHashes := make([]string, len(outputScripts))

	for i, outputScript := range outputScripts {
		indexHashes[i] = electrum.GetIndexHash(outputScript)
	}

	return indexHashes, nil
}

// getOutputScripts creates all the scripts that send to an list of Bitcoin address.
func getOutputScripts(addresses []libwallet.MuunAddress, chainParams *chaincfg.Params) ([][]byte, error) {
	outputScripts := make([][]byte, len(addresses))

	for i, address := range addresses {
		rawAddress := address.Address()

		decodedAddress, err := btcutilw.DecodeAddress(rawAddress, chainParams)
		if err != nil {
			return nil, fmt.Errorf("failed to decode address %s: %w", rawAddress, err)
		}

		outputScript, err := txscriptw.PayToAddrScript(decodedAddress)
		if err != nil {
			return nil, fmt.Errorf("failed to craft script for %s: %w", rawAddress, err)
		}

		outputScripts[i] = outputScript
	}

	return outputScripts, nil
}
