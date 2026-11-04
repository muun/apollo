package nfc

// ChallengeResponseV3 is what the card returns after a successful
// SIGN_CHALLENGE.
//
// The MAC is what authenticates the response to the server, which shares the
// card's secret. CommitmentSig is a separate, asymmetric statement: only the
// card holds the key behind it, so the server cannot forge a record of the
// card having approved an action it did not.
type ChallengeResponseV3 struct {
	CardPublicKey []byte // P (65 bytes) - fresh per challenge
	MAC           []byte // 32 bytes - HMAC over tag || C || P

	// CommitmentSig is a DER-encoded ECDSA signature over
	// "card-approval" || C || HASH(reason), made with the card's
	// commitment key.
	CommitmentSig []byte
}
