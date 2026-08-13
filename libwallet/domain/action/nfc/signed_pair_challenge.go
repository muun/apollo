package nfc

import "github.com/muun/libwallet/domain/nfc"

// SignedPairChallenge bundles the output of the NFC sign step into a
// single value that the submit step consumes. It lives in the action
// package — not in domain/model/security_card alongside the other pair
// lifecycle types — because it carries *nfc.PairingResponse, and the
// model package can't depend on domain/nfc without creating an import
// cycle (domain/nfc already depends on the model package via the sign
// flow). The action package is the only layer where importing both is
// legal.
type SignedPairChallenge struct {
	PairingResponse *nfc.PairingResponse
	// ClientPublicKey is forwarded to the mock's MAC check today. See
	// the TODO in PairSignChallengeAction.Run for the eventual removal
	// once the card firmware drops the pub_client requirement.
	ClientPublicKey []byte
}
