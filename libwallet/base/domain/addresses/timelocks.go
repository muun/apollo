package addresses

import (
	"math"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/platform/preconditions"
)

// checkRelativeTimelock asserts that blocks fits a block-based BIP68 relative
// timelock. Panics if blocks is outside [1, 65535].
func checkRelativeTimelock(blocks int64) {
	// BIP68 relative timelocks in blocks are 16 bits wide.
	preconditions.CheckStatef(
		blocks > 0 && blocks <= math.MaxUint16,
		"relative timelock must be in [1, %d] blocks, got %d", math.MaxUint16, blocks,
	)
}

// checkAbsoluteTimelock asserts that height fits a BIP65 block-height absolute
// timelock. Panics if height is zero, negative or in the timestamp range.
func checkAbsoluteTimelock(height int64) {
	preconditions.CheckStatef(
		height > 0 && height < txscript.LockTimeThreshold,
		"absolute timelock must be a block height in [1, %d], got %d",
		int64(txscript.LockTimeThreshold)-1, height,
	)
}

// checkRelativeTimelockSpend asserts that the input at index commits to a
// BIP68 relative timelock of at least blocks. Panics if blocks is out of
// range, index is not an input of tx, the transaction version predates BIP68
// or the input's sequence disables the timelock, encodes seconds or encodes
// fewer blocks.
func checkRelativeTimelockSpend(tx *wire.MsgTx, index int, blocks int64) {
	checkRelativeTimelock(blocks)
	preconditions.CheckStatef(
		index >= 0 && index < len(tx.TxIn),
		"input index %d out of range for %d inputs", index, len(tx.TxIn),
	)

	sequence := tx.TxIn[index].Sequence
	preconditions.CheckStatef(
		tx.Version >= 2, "BIP68 needs a version 2 transaction, got %d", tx.Version,
	)
	preconditions.CheckStatef(
		sequence&wire.SequenceLockTimeDisabled == 0,
		"input sequence %#x disables its relative timelock", sequence,
	)
	preconditions.CheckStatef(
		sequence&wire.SequenceLockTimeIsSeconds == 0,
		"input sequence %#x encodes seconds, blocks are required", sequence,
	)
	preconditions.CheckStatef(
		int64(sequence&wire.SequenceLockTimeMask) >= blocks,
		"input sequence must encode at least %d blocks, got %d",
		blocks, sequence&wire.SequenceLockTimeMask,
	)
}

// checkAbsoluteTimelockSpend asserts that the transaction commits to a BIP65
// absolute timelock of at least height. Panics if height is out of range,
// index is not an input of tx, nLockTime is a timestamp or below height, or
// the input's sequence is final, which disables nLockTime.
func checkAbsoluteTimelockSpend(tx *wire.MsgTx, index int, height int64) {
	checkAbsoluteTimelock(height)
	preconditions.CheckStatef(
		index >= 0 && index < len(tx.TxIn),
		"input index %d out of range for %d inputs", index, len(tx.TxIn),
	)

	preconditions.CheckStatef(
		int64(tx.LockTime) >= height && tx.LockTime < txscript.LockTimeThreshold,
		"nLockTime must be a block height in [%d, %d), got %d",
		height, int64(txscript.LockTimeThreshold), tx.LockTime,
	)
	preconditions.CheckStatef(
		tx.TxIn[index].Sequence != wire.MaxTxInSequenceNum,
		"a final input sequence disables nLockTime",
	)
}
