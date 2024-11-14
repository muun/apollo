package musig

import (
	"crypto/hmac"
	"crypto/sha512"
	"encoding/binary"
	"fmt"

	"github.com/btcsuite/btcd/btcec/v2"
	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

type bip32TweakStep struct {
	depth       uint8
	pubKeyBytes []byte
	chainCode   []byte
	tweakBytes  [32]byte
}

// The following function receives an aggregatedKey and the unhardened
// derivation steps to produce a list of MuSig KeyTweaks. Those tweaks are used
// to produce a valid signature for the fresly derived key. To only derive the
// xpub, BIP32 can be used with a special chaincode. See the tests or BIP328
// for details.
func getBip32TweaksForAggregatedKey(aggregatedKey *secp256k1.PublicKey, path []uint32) (*bip32TweakStep, []musig2v100.KeyTweakDesc, error) {
	// chainCode := SHA256("MuSig2MuSig2MuSig2")
	chainCode := []byte{
		0x86, 0x80, 0x87, 0xca, 0x02, 0xa6, 0xf9, 0x74,
		0xc4, 0x59, 0x89, 0x24, 0xc3, 0x6b, 0x57, 0x76,
		0x2d, 0x32, 0xcb, 0x45, 0x71, 0x71, 0x67, 0xe3,
		0x00, 0x62, 0x2c, 0x71, 0x67, 0xe3, 0x89, 0x65,
	}

	var tweaks []musig2v100.KeyTweakDesc
	var err error

	tweakContext := &bip32TweakStep{
		chainCode:   chainCode,
		depth:       0,
		pubKeyBytes: aggregatedKey.SerializeCompressed(),
	}

	for _, i := range path {
		tweakContext, err = tweakContext.child(i)
		if err != nil {
			return nil, nil, err
		}
		tweaks = append(tweaks, musig2v100.KeyTweakDesc{
			IsXOnly: false,
			Tweak:   tweakContext.tweakBytes,
		})
	}

	return tweakContext, tweaks, nil
}

// Performs a single BIP32 derivation step. The btcec implementation of
// ExtendedKey is not used here because it does not expose the Il value, which
// is needed to produce the tweaks.
func (parent *bip32TweakStep) child(i uint32) (*bip32TweakStep, error) {
	// Prevent derivation of children beyond the max allowed depth.
	if parent.depth == 255 {
		return nil, fmt.Errorf("trying to derive avobe max depth")
	}

	if i >= 0x80000000 {
		return nil, fmt.Errorf("trying to derive a hardened MuSig key")
	}

	// let data = serialize(parentPubKey) || serializeU32(i)
	keyLen := 33
	data := make([]byte, keyLen+4)
	copy(data, parent.pubKeyBytes)               // write key
	binary.BigEndian.PutUint32(data[keyLen:], i) // write i)

	// Take the HMAC-SHA512 of the current key's chain code and the derived data:
	//
	//   I = HMAC-SHA512(Key = chainCode, Data = data)
	hmac512 := hmac.New(sha512.New, parent.chainCode)
	hmac512.Write(data)
	ilr := hmac512.Sum(nil)

	// Split "I" into two 32-byte sequences Il and Ir where:
	//
	//   Il = intermediate key used to derive the child
	//   Ir = child chain code
	il := ilr[:32]
	childChainCode := ilr[32:]

	// Both derived public or private keys rely on treating the left 32-byte
	// sequence calculated above (Il) as a 256-bit integer that must be
	// within the valid range for a secp256k1 private key.  There is a small
	// chance (< 1 in 2^127) this condition will not hold, and in that case,
	// a child extended key can't be created for this index and the caller
	// should simply increment to the next index.
	ilNum := new(btcec.ModNScalar)
	overflows := ilNum.SetBytes((*[32]byte)(il))
	if overflows > 0 {
		return nil, fmt.Errorf("generated IL overflows P %d", overflows)
	}

	// Convert the serialized compressed parent public key into X
	// and Y coordinates so it can be added to the intermediate child key.
	parentKeyCoordinates, err := btcec.ParseJacobian(parent.pubKeyBytes)
	if err != nil {
		return nil, err
	}

	// Add the intermediate child key to the parent public key to derive
	// the final child key.
	//
	//   childKey = parse256(Il)*G + parentKey
	var childKey btcec.JacobianPoint
	btcec.ScalarBaseMultNonConst(ilNum, &childKey)                 // childKey = Il*G
	btcec.AddNonConst(&childKey, &parentKeyCoordinates, &childKey) // childKey += parentKey
	childKey.ToAffine()
	pubKeyBytes := btcec.NewPublicKey(&childKey.X, &childKey.Y).SerializeCompressed()

	ret := bip32TweakStep{
		chainCode:   childChainCode,
		depth:       parent.depth + 1,
		pubKeyBytes: pubKeyBytes,
		tweakBytes:  ([32]byte)(il),
	}

	return &ret, nil
}
