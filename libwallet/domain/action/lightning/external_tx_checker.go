package lightning

import (
	"context"
	"maps"
	"slices"

	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	model "github.com/muun/libwallet/domain/model/lightning"
	electrum "github.com/muun/libwallet/electrum/v2"
	"github.com/muun/libwallet/platform/observability/slogctx"
)

type (
	// ExternalTxCheckerAction returns true when it's not possible to
	// broadcast all the txs to check due to their outputs having been
	// irrevocably spent by external txs.
	ExternalTxCheckerAction interface {
		Run(
			ctx context.Context, currentHeight int,
			batch *model.IncomingHTLCBatch, txsToCheck []model.Transaction,
		) (bool, error)
	}

	externalTxCheckerAction struct {
		electrumClient electrum.Client
	}

	checkerState struct {
		txInputSets  []map[wire.OutPoint]bool // input set per enforcer Tx
		allOutpoints map[wire.OutPoint]bool   // union of all txInputSets
		scriptHashes []string                 // the scripthashes for all outpoints we care about
		knownTxIDs   map[string]bool          // IDs for first/second stage Txs plus their parents
	}
)

func NewExternalTxCheckerAction(electrumClient electrum.Client) ExternalTxCheckerAction {
	return &externalTxCheckerAction{
		electrumClient: electrumClient,
	}
}

func (c *externalTxCheckerAction) Run(
	ctx context.Context,
	currentHeight int,
	batch *model.IncomingHTLCBatch,
	txsToCheck []model.Transaction,
) (bool, error) {
	// Collect Txs inputs, resolve their parent Txs, and compute scripthashes.
	state, err := c.resolveOutpoints(ctx, batch, txsToCheck)
	if err != nil || state == nil {
		return false, err
	}

	// If any Tx still has all its inputs unspent, it could still be broadcast.
	// Note: this is an optimization to avoid executing more costly Electrum
	// calls that fetch external TXs and verify whether they are settled.
	allBlocked, err := c.noTxHasAllInputsUnspent(ctx, state)
	if err != nil || !allBlocked {
		return false, err
	}

	// All outpoints are spent. Verify that the external spender has 6+ confirmations.
	settled, err := c.hasIrrevocableExternalSpenders(ctx, currentHeight, state)
	if err != nil || !settled {
		return false, err
	}

	slogctx.Info(ctx,
		"All parent outputs irrevocably spent by external transactions, stopping enforcement.",
		"batch", batch.UUID,
	)
	return true, nil
}

// resolveOutpoints builds the checkerState or returns nil when there is nothing to check.
// The checkerState will be used to determine whether we can continue the enforcement process.
func (c *externalTxCheckerAction) resolveOutpoints(
	ctx context.Context,
	batch *model.IncomingHTLCBatch,
	txsToCheck []model.Transaction,
) (*checkerState, error) {
	// Index all batch Txs so we can resolve intra-batch parent references locally instead of paying the cost
	// of fetching them via Electrum.
	var allBatchTxs []*model.Transaction
	for _, recall := range batch.Recalls {
		allBatchTxs = append(allBatchTxs, &recall.Recall, &recall.RecallIncomingSuccess)
	}
	for _, enforcement := range batch.Enforcements {
		allBatchTxs = append(
			allBatchTxs,
			&enforcement.Enforcement,
			&enforcement.EnforcementIncomingSuccess,
		)
	}

	knownTxIDs := make(map[string]bool, len(allBatchTxs))
	enforcerTxsByID := make(map[string]*model.Transaction, len(allBatchTxs))
	for _, tx := range allBatchTxs {
		knownTxIDs[tx.GetID()] = true
		enforcerTxsByID[tx.GetID()] = tx
	}

	// Build outpoints and per-Tx input sets only from txsToCheck.
	allOutpoints := make(map[wire.OutPoint]bool)
	txInputSets := make([]map[wire.OutPoint]bool, 0, len(txsToCheck))
	for i := range txsToCheck {
		tx := &txsToCheck[i]
		inputSet := make(map[wire.OutPoint]bool, len(tx.TxIn))
		for _, txIn := range tx.TxIn {
			op := txIn.PreviousOutPoint
			inputSet[op] = true
			allOutpoints[op] = true
		}
		txInputSets = append(txInputSets, inputSet)
	}

	if len(allOutpoints) == 0 {
		return nil, nil
	}

	// Resolve parent transactions: use Txs we already hold for parents that are part of
	// the batch (e.g. Recall is the parent of RecallIncomingSuccess), fetch the rest.
	// TODO(lightning): revisit whether we still need to fetch missing parents once
	// we're more advanced in M2U protocol at which point we may know the parent Txs
	// of enforcer/recalls (and thus we can skip fetching them).
	parentTxs := make(map[string]*model.Transaction)
	var missingParentIDs []string
	for op := range allOutpoints {
		if parentTxs[op.Hash.String()] != nil {
			continue
		}
		if tx := enforcerTxsByID[op.Hash.String()]; tx != nil {
			parentTxs[op.Hash.String()] = tx
		} else {
			missingParentIDs = append(missingParentIDs, op.Hash.String())
		}
	}

	if len(missingParentIDs) > 0 {
		parentTxResults, err := c.electrumClient.GetTransactionBatch(ctx, missingParentIDs)
		if err != nil {
			return nil, errors.Errorf("get parent transactions for batch %s: %w", batch.UUID, err)
		}

		for txID, result := range parentTxResults {
			rawHex, err := result.Unwrap()
			if result.ElectrumError() != nil {
				continue
			} else if err != nil {
				return nil, errors.Errorf("get parent transaction %s: %w", txID, err)
			}
			tx, err := model.NewTransaction(rawHex)
			if err != nil {
				return nil, errors.Errorf("deserialize parent transaction %s: %w", txID, err)
			}
			parentTxs[txID] = tx
			knownTxIDs[txID] = true
		}
	}

	// Keep only outpoints whose parent Tx was resolved, and compute their scripthashes.
	resolvedOutpoints := make(map[wire.OutPoint]bool)
	scriptHashSet := make(map[string]bool)

	for op := range allOutpoints {
		parentTx := parentTxs[op.Hash.String()]
		if parentTx == nil || int(op.Index) >= len(parentTx.TxOut) {
			continue
		}
		resolvedOutpoints[op] = true
		scriptHash := electrum.GetScriptHash(parentTx.TxOut[op.Index].PkScript)
		scriptHashSet[scriptHash] = true
	}

	if len(resolvedOutpoints) == 0 {
		return nil, nil
	}

	// Filter txInputSets to only include resolved outpoints.
	var resolvedInputSets []map[wire.OutPoint]bool
	for _, inputSet := range txInputSets {
		resolved := make(map[wire.OutPoint]bool)
		for op := range inputSet {
			if resolvedOutpoints[op] {
				resolved[op] = true
			}
		}
		if len(resolved) > 0 {
			resolvedInputSets = append(resolvedInputSets, resolved)
		}
	}

	return &checkerState{
		txInputSets:  resolvedInputSets,
		allOutpoints: resolvedOutpoints,
		scriptHashes: slices.Collect(maps.Keys(scriptHashSet)),
		knownTxIDs:   knownTxIDs,
	}, nil
}

// noTxHasAllInputsUnspent returns true when no Tx has all its inputs still in the UTxO set.
func (c *externalTxCheckerAction) noTxHasAllInputsUnspent(
	ctx context.Context,
	state *checkerState,
) (bool, error) {
	unspentResults, err := c.electrumClient.ListUnspentBatch(ctx, state.scriptHashes)
	if err != nil {
		return false, err
	}

	unspentSet := make(map[wire.OutPoint]bool)
	for scriptHash, result := range unspentResults {
		unspents, err := result.Unwrap()
		if err != nil {
			return false, errors.Errorf("list unspent for scripthash %s: %w", scriptHash, err)
		}
		for _, u := range unspents {
			hash, err := chainhash.NewHashFromStr(u.TxHash)
			if err != nil {
				return false, err
			}
			op := wire.OutPoint{Hash: *hash, Index: uint32(u.TxPos)}
			if state.allOutpoints[op] {
				unspentSet[op] = true
			}
		}
	}

	for _, inputSet := range state.txInputSets {
		allUnspent := true
		for op := range inputSet {
			if !unspentSet[op] {
				allUnspent = false
				break
			}
		}
		if allUnspent {
			return false, nil
		}
	}

	return true, nil
}

// hasIrrevocableExternalSpenders returns true when at least one external spender exists and all have 6+ confs.
func (c *externalTxCheckerAction) hasIrrevocableExternalSpenders(
	ctx context.Context,
	currentHeight int,
	state *checkerState,
) (bool, error) {
	historyResults, err := c.electrumClient.GetScriptHashHistoryBatch(ctx, state.scriptHashes)
	if err != nil {
		return false, err
	}

	heightByExternalTxID := make(map[string]int)
	for scriptHash, result := range historyResults {
		entries, err := result.Unwrap()
		if err != nil {
			return false, errors.Errorf("get history for scripthash %s: %w", scriptHash, err)
		}
		for _, entry := range entries {
			if !state.knownTxIDs[entry.TxHash] {
				heightByExternalTxID[entry.TxHash] = int(entry.Height)
			}
		}
	}

	// No external transactions found in the histories — nothing to check.
	if len(heightByExternalTxID) == 0 {
		return false, nil
	}

	externalIDs := slices.Collect(maps.Keys(heightByExternalTxID))

	externalTxResults, err := c.electrumClient.GetTransactionBatch(ctx, externalIDs)
	if err != nil {
		return false, err
	}

	for txID, result := range externalTxResults {
		rawHex, err := result.Unwrap()
		if result.ElectrumError() != nil {
			continue // Tx not found on-chain, skip
		} else if err != nil {
			return false, errors.Errorf("get external transaction %s: %w", txID, err)
		}
		tx, err := model.NewTransaction(rawHex)
		if err != nil {
			return false, errors.Errorf("deserialize external transaction %s: %w", txID, err)
		}

		// Check if we find one of our outpoints being referenced by the inputs of the external Tx
		var op *wire.OutPoint
		for _, txIn := range tx.TxIn {
			if state.allOutpoints[txIn.PreviousOutPoint] {
				// This input references one of our outpoints
				op = &txIn.PreviousOutPoint
				break
			}
		}

		if op == nil {
			// No input of the external Tx references one of our outpoints, continue with the next Tx.
			continue
		}

		// By now, we know that an external transaction has spent at least one of the
		// outpoints referenced by one of our transactions.
		// Now we check whether that external Tx has reached settlement status.
		height := heightByExternalTxID[txID]
		if height > 0 && currentHeight-height+1 >= settledConfirmations {
			slogctx.Info(ctx,
				"Detected tx with at least one of its inputs double spent by an external tx",
				"local_tx", tx.TxHash().String(),
				"outpoint_tx_hash", op.Hash.String(),
				"outpoint_index", op.Index,
				"external_tx_id", txID,
			)
			return true, nil
		}
	}

	return false, nil
}
