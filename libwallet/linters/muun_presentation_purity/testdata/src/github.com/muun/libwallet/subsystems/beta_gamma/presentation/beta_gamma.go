package presentation

type BetaGamma struct { // want `the sub-system's contract is an interface: BetaGamma is not one`
	Pair pairAction
}

type pairAction interface {
	Run() error
}
