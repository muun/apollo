package musig

import (
	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"

	"github.com/muun/libwallet/platform/preconditions"
)

// MuSig2Tweaks is a struct that contains all tweaks that can be applied to a
// MuSig2 combined public key.
type MuSig2Tweaks struct {
	// GenericTweaks is a list of normal tweaks to apply to the combined
	// public key (and to the private key when signing).
	GenericTweaks []musig2v100.KeyTweakDesc

	// TaprootBIP0086Tweak indicates that the final key should use the
	// taproot tweak as defined in BIP 341, with the BIP 86 modification:
	//     outputKey = internalKey + h_tapTweak(internalKey)*G.
	// In this case, the aggregated key before the tweak will be used as
	// the internal key.
	TaprootBIP0086Tweak bool

	// TaprootTweak specifies that the final key should use the
	// taproot tweak as defined in BIP 341:
	//     outputKey = internalKey + h_tapTweak(internalKey || scriptRoot).
	// In this case, the aggregated key before the tweak will be used as
	// the internal key.
	TaprootTweak []byte

	// Unhardened derivation path specifies the unhardened path to follow in
	// order to derive the final key based on BIP32 + BIP328. This property
	// produces a list of GenericTweaks to be processed AFTER the provided
	// GenericTweaks
	UnhardenedDerivationPath []uint32
}

// HasTaprootTweak returns true if either a taproot BIP0086 tweak or a taproot
// script root tweak is set.
func (t *MuSig2Tweaks) HasTaprootTweak() bool {
	return t.TaprootBIP0086Tweak || len(t.TaprootTweak) > 0
}

// If the spending conditions do not require a script path, the
// output key should commit to an unspendable script path instead
// of having no script path. This can be achieved by computing the
// output key point as Q = P + int(hashTapTweak(bytes(P)))G.
func KeySpendOnlyTweak() *MuSig2Tweaks {
	return &MuSig2Tweaks{
		TaprootBIP0086Tweak: true,
	}
}

// Create tweak for taproot script, the argument is the TapHash of the root node.
func TapScriptTweak(rootNodeHash []byte) *MuSig2Tweaks {
	return &MuSig2Tweaks{
		TaprootTweak: rootNodeHash,
	}
}

// Creates a tweak tha performs no operations over the final key.
//
// Use this tweak to SIGN during a tapscript spend path.
// Use this tweak to obtain the internal key of musig.
func NoopTweak() *MuSig2Tweaks {
	return &MuSig2Tweaks{}
}

// Mutates the current tweaks and sets a new unhardened derivation path for it.
//
// May only be used on NoopTweak: an unhardened derivation path cannot be combined with any other
// tweak type.
func (t *MuSig2Tweaks) WithUnhardenedDerivationPath(path []uint32) *MuSig2Tweaks {
	t.UnhardenedDerivationPath = path
	t.checkSingleTweakType()

	return t
}

// checkSingleTweakType panics if more than one tweak type is set.
//
// While MuSig2Tweaks supports combining different types of tweaks, its callers currently do not
// support it: they apply the first type they find and drop the rest.
func (t *MuSig2Tweaks) checkSingleTweakType() {
	tweakTypesUsed := 0
	if len(t.GenericTweaks) > 0 {
		tweakTypesUsed++
	}
	if t.TaprootBIP0086Tweak {
		tweakTypesUsed++
	}
	if len(t.TaprootTweak) > 0 {
		tweakTypesUsed++
	}
	if len(t.UnhardenedDerivationPath) > 0 {
		tweakTypesUsed++
	}

	preconditions.CheckStatef(
		tweakTypesUsed <= 1,
		"combining tweaks of different types is not supported",
	)
}
