package simulator

// cardGenerationV3 is one generation of the V3 firmware as Houston sees it:
// how it lays out the metadata a card sends in its PAIR response, where the
// card's keys are in it, and which secret keys its challenge response MAC.
//
// Houston reads a pairing with every generation it knows and keeps the one
// whose reading the MAC verifies. A wrong reading yields another
// authentication form, or another key agreement key, so its MAC never
// verifies: the MAC is what names the generation. The simulator keeps no record
// of it for the challenges that follow; see pairedCardGenerationV3.
type cardGenerationV3 interface {
	// Name identifies the generation in logs.
	Name() string

	// ParseMetadata parses the metadata a card sent in its PAIR response into
	// what Houston needs to verify the pairing. The bytes must be exactly
	// what the card emitted: the MAC covers the values rather than the
	// buffer, so bytes past them are refused rather than stored.
	ParseMetadata(metadata []byte) (*metadataV3, error)

	// ChallengeResponseKey returns the secret a card of this generation keys
	// its challenge response MAC with: secret, the card secret the challenge
	// was authenticated under, or nextSecret, the one the ratchet derives
	// from it.
	ChallengeResponseKey(secret, nextSecret []byte) []byte
}

// cardGenerationsV3 lists the generations Houston reads, newest first: the
// ones with cards in the field and the next one as the protocol spec lays
// it out. A pairing is attributed to the first one that reads it.
//
// A generation is a wire layout and the challenge response that goes with
// it, named after the firmware that introduced them. A firmware that keeps
// both from the one before adds nothing here: the version it reports comes
// out of the metadata, read by the same generation. A generation is retired
// by removing it from here.
var cardGenerationsV3 = []cardGenerationV3{
	cardGenerationV3_1{},
	cardGenerationV3_0{},
}
