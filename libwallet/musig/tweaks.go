package musig

import (
	"github.com/btcsuite/btcd/btcec/v2"
	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/muun/libwallet/musig2v040"
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
	// In this case, the aggregated key before the tweak will be used as the
	// internal key. If this is set to true then TaprootTweak will be
	// ignored.
	TaprootBIP0086Tweak bool

	// TaprootTweak specifies that the final key should use the taproot
	// tweak as defined in BIP 341:
	//     outputKey = internalKey + h_tapTweak(internalKey || scriptRoot).
	// In this case, the aggregated key before the tweak will be used as the
	// internal key. Will be ignored if TaprootBIP0086Tweak is set to true.
	TaprootTweak []byte

	// Unhardened derivation path specifies the unhardened path to follow in
	// order to derivate the final key based on BIP32 + BIP328. This property
	// produces a list of GenericTweaks to be processed AFTER the provided
	// GenericTweaks
	UnhardenedDerivationPath []uint32
}

// HasTaprootTweak returns true if either a taproot BIP0086 tweak or a taproot
// script root tweak is set.
func (t *MuSig2Tweaks) HasTaprootTweak() bool {
	return t.TaprootBIP0086Tweak || len(t.TaprootTweak) > 0
}

// ToContextOptions converts the tweak descriptor to context options.
func (t *MuSig2Tweaks) ToContextOptions(allSignerPubKeys []*btcec.PublicKey) ([]musig2v100.ContextOption, error) {
	var tweakOpts []musig2v100.ContextOption

	if len(t.GenericTweaks) > 0 {
		tweakOpts = append(tweakOpts, musig2v100.WithTweakedContext(
			t.GenericTweaks...,
		))
	}

	if len(t.UnhardenedDerivationPath) > 0 {
		bip328tweaks, err := getKeyDerivationTweaksForMusig(
			allSignerPubKeys,
			t.UnhardenedDerivationPath)
		if err != nil {
			return nil, err
		}
		tweakOpts = append(tweakOpts, musig2v100.WithTweakedContext(
			bip328tweaks...,
		))
	}

	// The BIP0086 tweak and the taproot script tweak are mutually
	// exclusive.
	if t.TaprootBIP0086Tweak {
		tweakOpts = append(tweakOpts, musig2v100.WithBip86TweakCtx())
	} else if len(t.TaprootTweak) > 0 {
		tweakOpts = append(tweakOpts, musig2v100.WithTaprootTweakCtx(
			t.TaprootTweak,
		))
	}

	return tweakOpts, nil
}

// ToV040ContextOptions converts the tweak descriptor to v0.4.0 context options.
func (t *MuSig2Tweaks) ToV040ContextOptions() []musig2v040.ContextOption {
	var tweakOpts []musig2v040.ContextOption

	if len(t.GenericTweaks) > 0 {
		genericTweaksCopy := make(
			[]musig2v040.KeyTweakDesc, len(t.GenericTweaks),
		)
		for idx := range t.GenericTweaks {
			genericTweaksCopy[idx] = musig2v040.KeyTweakDesc{
				Tweak:   t.GenericTweaks[idx].Tweak,
				IsXOnly: t.GenericTweaks[idx].IsXOnly,
			}
		}
		tweakOpts = append(tweakOpts, musig2v040.WithTweakedContext(
			genericTweaksCopy...,
		))
	}

	// The BIP0086 tweak and the taproot script tweak are mutually
	// exclusive.
	if t.TaprootBIP0086Tweak {
		tweakOpts = append(tweakOpts, musig2v040.WithBip86TweakCtx())
	} else if len(t.TaprootTweak) > 0 {
		tweakOpts = append(tweakOpts, musig2v040.WithTaprootTweakCtx(
			t.TaprootTweak,
		))
	}

	return tweakOpts
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
func (t *MuSig2Tweaks) WithUnhardenedDerivationPath(path []uint32) *MuSig2Tweaks {
	t.UnhardenedDerivationPath = path
	return t
}
