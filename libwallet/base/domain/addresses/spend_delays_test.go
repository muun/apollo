package addresses

import (
	"testing"

	"github.com/stretchr/testify/require"
)

// TestNonCollaborativeDelays_MatchTheSpec checks the delay every output's
// non-collaborative leaf waits. It must not shorten the delays through
// setSpendDelaysForTest.
func TestNonCollaborativeDelays_MatchTheSpec(t *testing.T) {
	const (
		toClientDelay           = int64(52_560)
		toClientWithdrawalDelay = int64(288)
		toServerDelay           = int64(1_008)
	)

	testCases := []struct {
		output string
		delay  func() int64
		want   int64
	}{
		{"V7", toClientV7NonCollaborativeDelay, toClientDelay},
		{"V8", toClientV8NonCollaborativeDelay, toClientDelay},
		{"V9", toClientV9NonCollaborativeDelay, toClientDelay},
		{"to_client_htlc", toClientHTLCNonCollaborativeDelay, toClientDelay},
		{
			"to_client_htlc_enforcement",
			toClientHTLCEnforcementNonCollaborativeDelay,
			toClientDelay,
		},
		{"to_client_revocable", toClientRevocableNonCollaborativeDelay, toClientDelay},
		{
			"to_client_withdrawal",
			toClientWithdrawalNonCollaborativeDelay,
			toClientWithdrawalDelay,
		},
		{"to_server", toServerNonCollaborativeDelay, toServerDelay},
		{"to_server_htlc", toServerHTLCNonCollaborativeDelay, toClientDelay},
		{
			"to_server_htlc_enforcement",
			toServerHTLCEnforcementNonCollaborativeDelay,
			toClientDelay,
		},
		{
			"to_server_htlc_withdrawal",
			toServerHTLCWithdrawalNonCollaborativeDelay,
			toClientWithdrawalDelay,
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.output, func(t *testing.T) {
			require.Equal(t, testCase.want, testCase.delay())
		})
	}
}
