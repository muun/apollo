package addresses

import (
	"fmt"
	"math"
	"testing"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"
)

func timelockTx(version int32, sequence, lockTime uint32) *wire.MsgTx {
	tx := wire.NewMsgTx(version)
	tx.AddTxIn(&wire.TxIn{Sequence: sequence})
	tx.LockTime = lockTime
	return tx
}

func TestCheckRelativeTimelock(t *testing.T) {
	testCases := []struct {
		name      string
		blocks    int64
		wantPanic string
	}{
		{
			name:      "smallest delta",
			blocks:    1,
			wantPanic: "",
		},
		{
			name:      "largest delta",
			blocks:    math.MaxUint16,
			wantPanic: "",
		},
		{
			name:      "zero",
			blocks:    0,
			wantPanic: "relative timelock must be in [1, 65535] blocks, got 0",
		},
		{
			name:      "negative",
			blocks:    -1,
			wantPanic: "relative timelock must be in [1, 65535] blocks, got -1",
		},
		{
			name:      "beyond 16 bits",
			blocks:    math.MaxUint16 + 1,
			wantPanic: "relative timelock must be in [1, 65535] blocks, got 65536",
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			check := func() { checkRelativeTimelock(testCase.blocks) }
			if testCase.wantPanic == "" {
				require.NotPanics(t, check)
			} else {
				require.PanicsWithError(t, testCase.wantPanic, check)
			}
		})
	}
}

func TestCheckAbsoluteTimelock(t *testing.T) {
	testCases := []struct {
		name      string
		height    int64
		wantPanic string
	}{
		{
			name:      "lowest height",
			height:    1,
			wantPanic: "",
		},
		{
			name:      "highest height",
			height:    txscript.LockTimeThreshold - 1,
			wantPanic: "",
		},
		{
			name:      "zero",
			height:    0,
			wantPanic: "absolute timelock must be a block height in [1, 499999999], got 0",
		},
		{
			name:      "timestamp range",
			height:    txscript.LockTimeThreshold,
			wantPanic: "absolute timelock must be a block height in [1, 499999999], got 500000000",
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			check := func() { checkAbsoluteTimelock(testCase.height) }
			if testCase.wantPanic == "" {
				require.NotPanics(t, check)
			} else {
				require.PanicsWithError(t, testCase.wantPanic, check)
			}
		})
	}
}

func TestCheckRelativeTimelockSpend(t *testing.T) {
	testCases := []struct {
		name      string
		version   int32
		sequence  uint32
		blocks    int64
		wantPanic string
	}{
		{
			name:      "exact block count",
			version:   2,
			sequence:  144,
			blocks:    144,
			wantPanic: "",
		},
		{
			name:      "larger block count",
			version:   2,
			sequence:  145,
			blocks:    144,
			wantPanic: "",
		},
		{
			name:      "version predates BIP68",
			version:   1,
			sequence:  144,
			blocks:    144,
			wantPanic: "BIP68 needs a version 2 transaction, got 1",
		},
		{
			name:      "final sequence disables the timelock",
			version:   2,
			sequence:  wire.MaxTxInSequenceNum,
			blocks:    144,
			wantPanic: "input sequence 0xffffffff disables its relative timelock",
		},
		{
			name:      "disable flag set",
			version:   2,
			sequence:  wire.SequenceLockTimeDisabled | 144,
			blocks:    144,
			wantPanic: "input sequence 0x80000090 disables its relative timelock",
		},
		{
			name:      "seconds flag set",
			version:   2,
			sequence:  wire.SequenceLockTimeIsSeconds | 144,
			blocks:    144,
			wantPanic: "input sequence 0x400090 encodes seconds, blocks are required",
		},
		{
			name:      "zero blocks rejected before decoding",
			version:   2,
			sequence:  144,
			blocks:    0,
			wantPanic: "relative timelock must be in [1, 65535] blocks, got 0",
		},
		{
			name:      "too few blocks",
			version:   2,
			sequence:  143,
			blocks:    144,
			wantPanic: "input sequence must encode at least 144 blocks, got 143",
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			tx := timelockTx(testCase.version, testCase.sequence, 0)
			check := func() { checkRelativeTimelockSpend(tx, 0, testCase.blocks) }
			if testCase.wantPanic == "" {
				require.NotPanics(t, check)
			} else {
				require.PanicsWithError(t, testCase.wantPanic, check)
			}
		})
	}
}

func TestCheckAbsoluteTimelockSpend(t *testing.T) {
	testCases := []struct {
		name      string
		sequence  uint32
		lockTime  uint32
		height    int64
		wantPanic string
	}{
		{
			name:      "exact height",
			sequence:  144,
			lockTime:  850_000,
			height:    850_000,
			wantPanic: "",
		},
		{
			name:      "larger height",
			sequence:  144,
			lockTime:  850_001,
			height:    850_000,
			wantPanic: "",
		},
		{
			name:      "highest block height",
			sequence:  144,
			lockTime:  txscript.LockTimeThreshold - 1,
			height:    850_000,
			wantPanic: "",
		},
		{
			name:      "zero height rejected before comparing",
			sequence:  144,
			lockTime:  850_000,
			height:    0,
			wantPanic: "absolute timelock must be a block height in [1, 499999999], got 0",
		},
		{
			name:      "height too low",
			sequence:  144,
			lockTime:  849_999,
			height:    850_000,
			wantPanic: "nLockTime must be a block height in [850000, 500000000), got 849999",
		},
		{
			name:      "nLockTime is a timestamp",
			sequence:  144,
			lockTime:  txscript.LockTimeThreshold,
			height:    850_000,
			wantPanic: "nLockTime must be a block height in [850000, 500000000), got 500000000",
		},
		{
			name:      "final sequence disables nLockTime",
			sequence:  wire.MaxTxInSequenceNum,
			lockTime:  850_000,
			height:    850_000,
			wantPanic: "a final input sequence disables nLockTime",
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			tx := timelockTx(2, testCase.sequence, testCase.lockTime)
			check := func() { checkAbsoluteTimelockSpend(tx, 0, testCase.height) }
			if testCase.wantPanic == "" {
				require.NotPanics(t, check)
			} else {
				require.PanicsWithError(t, testCase.wantPanic, check)
			}
		})
	}
}

func TestCheckTimelockSpendRejectsOutOfRangeIndex(t *testing.T) {
	tx := timelockTx(2, 144, 850_000)
	for _, index := range []int{-1, 1} {
		wantPanic := fmt.Sprintf("input index %d out of range for 1 inputs", index)
		require.PanicsWithError(t, wantPanic, func() { checkRelativeTimelockSpend(tx, index, 144) })
		require.PanicsWithError(
			t, wantPanic, func() { checkAbsoluteTimelockSpend(tx, index, 850_000) },
		)
	}
}
