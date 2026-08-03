package security_card

// SecurityCardSignChallengeV3 carries everything a card needs to
// verify and sign a challenge: the server's ephemeral key, the
// monotonic counter, the pairing index, the human-readable reason
// (bound into the MAC), and the server-computed MAC.
type SecurityCardSignChallengeV3 struct {
	ServerPublicKey []byte // C (65 bytes) - server's ephemeral public key
	Counter         uint16 // count_card, strictly increasing for anti-replay
	Index           uint16 // pairing index on the card (V3 is always 0x0000)
	Reason          []byte // human-readable action description, bound into the MAC
	MAC             []byte // HMAC over tag || C || counter || index || reason
}
