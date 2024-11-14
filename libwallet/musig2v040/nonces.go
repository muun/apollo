// Copyright 2013-2022 The btcsuite developers

package musig2v040

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"io"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
)

var (
	// NonceAuxTag is the tag used to optionally mix in the secret key with
	// the set of aux randomness.
	NonceAuxTag = []byte("MuSig/aux")

	// NonceGenTag is used to generate the value (from a set of required an
	// optional field) that will be used as the part of the secret nonce.
	NonceGenTag = []byte("MuSig/nonce")
)

// secNonceToPubNonce takes our two secrete nonces, and produces their two
// corresponding EC points, serialized in compressed format.
func secNonceToPubNonce(secNonce [musig2.SecNonceSize]byte) [musig2.PubNonceSize]byte {
	var k1Mod, k2Mod btcec.ModNScalar
	k1Mod.SetByteSlice(secNonce[:btcec.PrivKeyBytesLen])
	k2Mod.SetByteSlice(secNonce[btcec.PrivKeyBytesLen:])

	var r1, r2 btcec.JacobianPoint
	btcec.ScalarBaseMultNonConst(&k1Mod, &r1)
	btcec.ScalarBaseMultNonConst(&k2Mod, &r2)

	// Next, we'll convert the key in jacobian format to a normal public
	// key expressed in affine coordinates.
	r1.ToAffine()
	r2.ToAffine()
	r1Pub := btcec.NewPublicKey(&r1.X, &r1.Y)
	r2Pub := btcec.NewPublicKey(&r2.X, &r2.Y)

	var pubNonce [musig2.PubNonceSize]byte

	// The public nonces are serialized as: R1 || R2, where both keys are
	// serialized in compressed format.
	copy(pubNonce[:], r1Pub.SerializeCompressed())
	copy(
		pubNonce[btcec.PubKeyBytesLenCompressed:],
		r2Pub.SerializeCompressed(),
	)

	return pubNonce
}

// NonceGenOption is a function option that allows callers to modify how nonce
// generation happens.
type NonceGenOption func(*nonceGenOpts)

// nonceGenOpts is the set of options that control how nonce generation
// happens.
type nonceGenOpts struct {
	// randReader is what we'll use to generate a set of random bytes. If
	// unspecified, then the normal crypto/rand rand.Read method will be
	// used in place.
	randReader io.Reader

	// secretKey is an optional argument that's used to further augment the
	// generated nonce by xor'ing it with this secret key.
	secretKey []byte

	// combinedKey is an optional argument that if specified, will be
	// combined along with the nonce generation.
	combinedKey []byte

	// msg is an optional argument that will be mixed into the nonce
	// derivation algorithm.
	msg []byte

	// auxInput is an optional argument that will be mixed into the nonce
	// derivation algorithm.
	auxInput []byte
}

// cryptoRandAdapter is an adapter struct that allows us to pass in the package
// level Read function from crypto/rand into a context that accepts an
// io.Reader.
type cryptoRandAdapter struct {
}

// Read implements the io.Reader interface for the crypto/rand package.  By
// default, we always use the crypto/rand reader, but the caller is able to
// specify their own generation, which can be useful for deterministic tests.
func (c *cryptoRandAdapter) Read(p []byte) (n int, err error) {
	return rand.Read(p)
}

// defaultNonceGenOpts returns the default set of nonce generation options.
func defaultNonceGenOpts() *nonceGenOpts {
	return &nonceGenOpts{
		randReader: &cryptoRandAdapter{},
	}
}

// WithCustomRand allows a caller to use a custom random number generator in
// place for crypto/rand. This should only really be used to generate
// determinstic tests.
func WithCustomRand(r io.Reader) NonceGenOption {
	return func(o *nonceGenOpts) {
		o.randReader = r
	}
}

// WithNonceSecretKeyAux allows a caller to optionally specify a secret key
// that should be used to augment the randomness used to generate the nonces.
func WithNonceSecretKeyAux(secKey *btcec.PrivateKey) NonceGenOption {
	return func(o *nonceGenOpts) {
		o.secretKey = secKey.Serialize()
	}
}

// WithNonceCombinedKeyAux allows a caller to optionally specify the combined
// key used in this signing session to further augment the randomness used to
// generate nonces.
func WithNonceCombinedKeyAux(combinedKey *btcec.PublicKey) NonceGenOption {
	return func(o *nonceGenOpts) {
		o.combinedKey = schnorr.SerializePubKey(combinedKey)
	}
}

// WithNonceMessageAux allows a caller to optionally specify a message to be
// mixed into the randomness generated to create the nonce.
func WithNonceMessageAux(msg [32]byte) NonceGenOption {
	return func(o *nonceGenOpts) {
		o.msg = msg[:]
	}
}

// WithNonceAuxInput is a set of auxiliary randomness, similar to BIP 340 that
// can be used to further augment the nonce generation process.
func WithNonceAuxInput(aux []byte) NonceGenOption {
	return func(o *nonceGenOpts) {
		o.auxInput = aux
	}
}

// genNonceAuxBytes writes out the full byte string used to derive a secret
// nonce based on some initial randomness as well as the series of optional
// fields. The byte string used for derivation is:
//   - let seed = tagged_hash(
//     "MuSig/nonce",
//     rand || len(aggpk) || aggpk || len(m) || m || len(in) || in
//     )
//   - return sha256(seed || i)
//
// where i is the ith secret nonce being generated.
//
// Muun only provides the rand parameter as sessionId. All other parameters are encoded as if they were len=0
func genNonceAuxBytes(rand []byte, i int) ([]byte, error) {
	var w bytes.Buffer

	// First, write out the randomness generated in the prior step.
	if _, err := w.Write(rand); err != nil {
		return nil, err
	}

	// write byte 0 for `sec_key`
	if err := w.WriteByte(0); err != nil {
		return nil, err
	}

	// write byte 0 for `aggpk`
	if err := w.WriteByte(0); err != nil {
		return nil, err
	}

	// write byte 0 for `message`
	if err := w.WriteByte(0); err != nil {
		return nil, err
	}

	// write byte 0 for `extra_input32`
	if err := w.WriteByte(0); err != nil {
		return nil, err
	}

	seed := chainhash.TaggedHash([]byte("MuSig/nonce"), w.Bytes())

	h := sha256.New()
	// write the seed to the buffer
	if _, err := h.Write(seed.CloneBytes()); err != nil {
		return nil, err
	}

	// Next we'll write out the interaction/index number which will
	// uniquely generate two nonces given the rest of the possibly static
	// parameters.
	ith := make([]byte, 1)
	ith[0] = uint8(i)

	if _, err := h.Write(ith); err != nil {
		return nil, err
	}

	return h.Sum(nil), nil
}

// Custom implementation of GenNonces to produce a bit-to-bit copy of the secp256k1_zkp
// All other parameters are ignored.
//
// Pseudo algorithm (|| means byte concat)
//
//	let seed = TaggedHash("MuSig/nonce", sessionId || 0 || 0 || 0 || 0)
//	let k = [sha256(seed || 0), sha256(seed || 1)]
//	let r = k*G
//	return toPublicKeyFormat(r)
func GenNonces(options ...NonceGenOption) (*musig2.Nonces, error) {
	opts := defaultNonceGenOpts()
	for _, opt := range options {
		opt(opts)
	}

	// First, we'll start out by generating 32 random bytes drawn from our
	// CSPRNG.
	var randBytes [32]byte
	if _, err := opts.randReader.Read(randBytes[:]); err != nil {
		return nil, err
	}

	// Using our randomness and the set of optional params, generate our
	// two secret nonces: k1 and k2.
	k1, err := genNonceAuxBytes(randBytes[:], 0)
	if err != nil {
		return nil, err
	}
	k2, err := genNonceAuxBytes(randBytes[:], 1)
	if err != nil {
		return nil, err
	}

	var k1Mod, k2Mod btcec.ModNScalar
	k1Mod.SetBytes((*[32]byte)(k1))
	k2Mod.SetBytes((*[32]byte)(k2))

	// The secret nonces are serialized as the concatenation of the two 32
	// byte secret nonce values.
	var nonces musig2.Nonces
	k1Mod.PutBytesUnchecked(nonces.SecNonce[:])
	k2Mod.PutBytesUnchecked(nonces.SecNonce[btcec.PrivKeyBytesLen:])

	// Next, we'll generate R_1 = k_1*G and R_2 = k_2*G. Along the way we
	// need to map our nonce values into mod n scalars so we can work with
	// the btcec API.
	nonces.PubNonce = secNonceToPubNonce(nonces.SecNonce)

	return &nonces, nil
}

// AggregateNonces aggregates the set of a pair of public nonces for each party
// into a single aggregated nonces to be used for multi-signing.
func AggregateNonces(pubNonces [][musig2.PubNonceSize]byte) ([musig2.PubNonceSize]byte, error) {
	// combineNonces is a helper function that aggregates (adds) up a
	// series of nonces encoded in compressed format. It uses a slicing
	// function to extra 33 bytes at a time from the packed 2x public
	// nonces.
	type nonceSlicer func([musig2.PubNonceSize]byte) []byte
	combineNonces := func(slicer nonceSlicer) (btcec.JacobianPoint, error) {
		// Convert the set of nonces into jacobian coordinates we can
		// use to accumulate them all into each other.
		pubNonceJs := make([]*btcec.JacobianPoint, len(pubNonces))
		for i, pubNonceBytes := range pubNonces {
			// Using the slicer, extract just the bytes we need to
			// decode.
			var nonceJ btcec.JacobianPoint

			nonceJ, err := btcec.ParseJacobian(slicer(pubNonceBytes))
			if err != nil {
				return btcec.JacobianPoint{}, err
			}

			pubNonceJs[i] = &nonceJ
		}

		// Now that we have the set of complete nonces, we'll aggregate
		// them: R = R_i + R_i+1 + ... + R_i+n.
		var aggregateNonce btcec.JacobianPoint
		for _, pubNonceJ := range pubNonceJs {
			btcec.AddNonConst(
				&aggregateNonce, pubNonceJ, &aggregateNonce,
			)
		}

		aggregateNonce.ToAffine()
		return aggregateNonce, nil
	}

	// The final nonce public nonce is actually two nonces, one that
	// aggregate the first nonce of all the parties, and the other that
	// aggregates the second nonce of all the parties.
	var finalNonce [musig2.PubNonceSize]byte
	combinedNonce1, err := combineNonces(func(n [musig2.PubNonceSize]byte) []byte {
		return n[:btcec.PubKeyBytesLenCompressed]
	})
	if err != nil {
		return finalNonce, err
	}

	combinedNonce2, err := combineNonces(func(n [musig2.PubNonceSize]byte) []byte {
		return n[btcec.PubKeyBytesLenCompressed:]
	})
	if err != nil {
		return finalNonce, err
	}

	copy(finalNonce[:], btcec.JacobianToByteSlice(combinedNonce1))
	copy(
		finalNonce[btcec.PubKeyBytesLenCompressed:],
		btcec.JacobianToByteSlice(combinedNonce2),
	)

	return finalNonce, nil
}
