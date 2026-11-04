package model

// PairProgress identifies a non-terminal milestone reached during
// the pair-and-submit flow. The action calls onProgress at each
// transition so the caller can react (e.g. relay it through a
// streaming RPC).
type PairProgress int

const (
	// PairProgressRefreshingChallenge fires just before the action
	// triggers the self-heal path because the persisted challenge
	// was missing or stale.
	PairProgressRefreshingChallenge PairProgress = iota
	// PairProgressChallengeSigned fires right after the signing
	// device produces its response, before the action calls Houston
	// to register.
	PairProgressChallengeSigned
)
