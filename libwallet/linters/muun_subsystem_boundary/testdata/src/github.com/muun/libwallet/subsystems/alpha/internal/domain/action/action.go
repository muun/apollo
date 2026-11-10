package action

import (
	alphamodel "github.com/muun/libwallet/subsystems/alpha/internal/domain/model"
	betamodel "github.com/muun/libwallet/subsystems/beta/domain/model" // want `cross-subsystem import: alpha imports beta's internal package "github.com/muun/libwallet/subsystems/beta/domain/model"; use beta/presentation instead`
	"github.com/muun/libwallet/subsystems/beta/presentation"
)

type Action struct {
	greeter presentation.Greeter
}

func (a Action) Run() string {
	return a.greeter.Greet(alphamodel.Name) + betamodel.Suffix
}
