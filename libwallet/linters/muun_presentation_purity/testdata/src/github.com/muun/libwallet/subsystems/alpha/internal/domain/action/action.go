package action

type GreetAction interface {
	Run(name string) (string, error)
}

type CountAction interface {
	Run() int
}
