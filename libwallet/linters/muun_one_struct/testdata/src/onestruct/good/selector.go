package good

// Selector is the file's namesake; txData is unexported support.
type Selector struct {
	rate float64
}

type txData struct {
	fee int64
}

func (s *Selector) Data() txData {
	return txData{fee: int64(s.rate)}
}
