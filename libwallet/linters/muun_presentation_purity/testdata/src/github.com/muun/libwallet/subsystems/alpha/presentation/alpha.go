package presentation

type Alpha interface {
	Greet(name string) (string, error)
	Count() int
}

type AlphaAdmin interface {
	Reset()
}
