package addresses

// spendDelays holds the relative timelocks, in blocks, the spec fixes for
// the non-collaborative spend paths of the Muun3 outputs.
type spendDelays struct {
	// toClientDelay is the year, older(52560), the user and cosigner wait to
	// sweep a to_client output without the lightning server. Every output
	// with a user-held balance carries the leaf.
	toClientDelay int64
	// toClientWithdrawalDelay is the two days, older(288), the user and
	// cosigner wait to take a withdrawal out of the channel, leaving the
	// server time to revoke it.
	toClientWithdrawalDelay int64
	// toServerDelay is the week, older(1008), the lightning server waits to
	// spend its to_server balance, leaving the user's watchtower time to
	// revoke it.
	toServerDelay int64
}

// specSpendDelays returns the delays the spec fixes.
func specSpendDelays() spendDelays {
	return spendDelays{
		toClientDelay:           52_560,
		toClientWithdrawalDelay: 288,
		toServerDelay:           1_008,
	}
}

// delays holds the spend delays every output commits to. Production code
// never writes it, so it always holds the spec's values; tests shorten it
// through setSpendDelaysForTest so their regtest spends mature within the
// test.
var delays = specSpendDelays()
