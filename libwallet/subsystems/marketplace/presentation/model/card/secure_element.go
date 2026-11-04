package card

type SecureElement int

const (
	SecureElementEAL5 SecureElement = iota
	SecureElementEAL5Plus
	SecureElementEAL6
	SecureElementEAL6Plus
)
