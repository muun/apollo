package testbitcoind

import "github.com/muun/libwallet/platform/preconditions"

// Node is one of the regtest bitcoind nodes the docker harness runs.
type Node string

const (
	// Regtest is the node every suite shares.
	Regtest Node = "regtest"
	// RegtestMusig runs a Core release able to sign musig() descriptors,
	// which Regtest only parses. CI starts it; locally it needs
	// `docker compose up regtest-musig`.
	RegtestMusig Node = "regtest-musig"
)

// host returns the node's RPC host and port.
func (n Node) host() string {
	switch n {
	case Regtest:
		return "localhost:18443"
	case RegtestMusig:
		return "localhost:38443"
	default:
		return preconditions.MissingCase(string(n))
	}
}
