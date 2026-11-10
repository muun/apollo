package presentation

import (
	"github.com/muun/libwallet/subsystems/alpha/internal/domain/action"
)

type Alpha struct {
	greet action.GreetAction
	count action.CountAction
}

func NewAlpha(greet action.GreetAction, count action.CountAction) Alpha {
	return Alpha{greet: greet, count: count}
}

func (a *Alpha) Greet(name string) (string, error) {
	return a.greet.Run(name)
}

func (a *Alpha) Count() int {
	return a.count.Run()
}
