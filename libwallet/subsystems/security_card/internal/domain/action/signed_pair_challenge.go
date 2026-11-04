package action

import (
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// SignedPairChallenge bundles the output of the NFC sign step into a
// single value that the submit step consumes. It lives in the action
// package — not in domain/model/security_card alongside the other pair
// lifecycle types — because it carries *nfc.PairingResponseV3, and the
// model package can't depend on domain/nfc without creating an import
// cycle (domain/nfc already depends on the model package via the sign
// flow). The action package is the only layer where importing both is
// legal.
type SignedPairChallenge struct {
	PairingResponse *nfc.PairingResponseV3

	// TappedCard is the card as its selection named it, which the submit
	// records as the paired one once Houston accepts the pairing.
	TappedCard *model.PairedCard
}
