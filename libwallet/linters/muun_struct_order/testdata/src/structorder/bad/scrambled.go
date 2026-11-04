package bad

type Scrambled struct {
	count int
}

func (s *Scrambled) touch() {
	s.count++
}

const interleaved = 1 // want `declaration interleaved in the Scrambled block: move it before or after the struct's definition, constructors and methods`

// Exported method after an unexported one: out of order.
func (s *Scrambled) Touch() { // want `exported methods of Scrambled must come before unexported methods: order is definition, interface assertions, constructors, exported methods, unexported methods`
	s.touch()
}

func NewScrambled() *Scrambled { // want `constructors of Scrambled must come before unexported methods: order is definition, interface assertions, constructors, exported methods, unexported methods`
	return &Scrambled{}
}
