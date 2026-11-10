package unrelated

// Out of the analyzer's scope: no diagnostics even with two exported structs.
type One struct{}

type Two struct{}
