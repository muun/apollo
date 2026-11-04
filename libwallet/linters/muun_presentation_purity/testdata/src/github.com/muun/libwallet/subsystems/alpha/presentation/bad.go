package presentation // want `presentation holds a single file, named after the sub-system: merge bad.go into alpha.go`

import (
	"github.com/muun/libwallet/subsystems/alpha/internal/domain/action" // want `the sub-system's contract imports nothing under internal/`
)

type Other struct { // want `presentation exports only interfaces: unexport type Other`
	Alpha Alpha
}

type Alias = Alpha // want `presentation exports only interfaces: unexport type Alias`

func NewAlpha(greet action.GreetAction) Alpha { // want `presentation exports only interfaces: move func NewAlpha`
	return nil
}

func Helper() {} // want `presentation exports only interfaces: move func Helper`

var Instance Alpha // want `presentation declares no exported values: move Instance`

const Name = "alpha" // want `presentation declares no exported values: move Name`
