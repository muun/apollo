package presentation // want `internal/presentation holds a single file, named after the sub-system: merge bad.go into alpha.go`

type Other struct { // want `internal/presentation exports only the Alpha struct: unexport type Other`
	Alpha Alpha
}

func (a Alpha) Describe() string { // want `methods of Alpha take a pointer receiver, so that only \*Alpha satisfies the contract: change Describe`
	return "alpha"
}

func (o *Other) Describe() string {
	return "other"
}

func Helper() {} // want `internal/presentation exports only Alpha and NewAlpha: unexport func Helper`

var Instance Alpha // want `internal/presentation declares no exported values: move Instance`

const Name = "alpha" // want `internal/presentation declares no exported values: move Name`
