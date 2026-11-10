package good

// Constants and support types go before or after a struct's block, never inside.
const defaultRetries = 3

// Broadcaster is the public face; the unexported struct + constructor follow the
// action pattern.
type Broadcaster interface {
	Broadcast() error
}

type publisher struct {
	retries int
}

var _ Broadcaster = (*publisher)(nil)

func NewPublisher() Broadcaster {
	return &publisher{retries: defaultRetries}
}

func NewPublisherWithRetries(retries int) Broadcaster {
	return &publisher{retries: retries}
}

func (p *publisher) Broadcast() error {
	return p.attempt()
}

func (p *publisher) attempt() error {
	return nil
}

// state is a support struct: its block sits entirely after the namesake's.
type state struct {
	attempts int
}

func (s *state) bump() {
	s.attempts++
}
