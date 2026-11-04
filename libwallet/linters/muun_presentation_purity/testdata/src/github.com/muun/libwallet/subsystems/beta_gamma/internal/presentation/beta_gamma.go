package presentation

type BetaGamma interface { // want `the sub-system's implementation is a struct: BetaGamma is not one`
	Pair() error
}
