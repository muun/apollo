package chainhashw

// This package adds some methods on top of chainhash. It's written to be both compatible and
// similar in implementation, so it's easy to swap out in the future.

import (
	"bytes"
	"crypto/sha256"

	"github.com/btcsuite/btcd/chaincfg/chainhash"
)

var knownTagPrefix = map[string][]byte{}

const (
	TagTapLeaf    = "TapLeaf"
	TagTapBranch  = "TapBranch"
	TagTapTweak   = "TapTweak"
	TagTapSighash = "TapSighash"
)

func init() {
	knownTagPrefix[TagTapLeaf] = calcTagPrefix(TagTapLeaf)
	knownTagPrefix[TagTapBranch] = calcTagPrefix(TagTapBranch)
	knownTagPrefix[TagTapTweak] = calcTagPrefix(TagTapTweak)
	knownTagPrefix[TagTapSighash] = calcTagPrefix(TagTapSighash)
}

func TagPrefix(tag string) []byte {
	if prefix, ok := knownTagPrefix[tag]; ok {
		return prefix
	}

	return calcTagPrefix(tag)
}

func TaggedHashB(tag string, data []byte) []byte {
	// NOTE: BIP-340 suggests optimizations that we don't make
	b := new(bytes.Buffer)
	b.Write(TagPrefix(tag))
	b.Write(data)

	return chainhash.HashB(b.Bytes())
}

func TaggedHashH(tag string, data []byte) chainhash.Hash {
	// NOTE: BIP-340 suggests optimizations that we don't make
	b := new(bytes.Buffer)
	b.Write(TagPrefix(tag))
	b.Write(data)

	return chainhash.HashH(b.Bytes())
}

func calcTagPrefix(tag string) []byte {
	tagHash := sha256.Sum256([]byte(tag))
	return append(tagHash[:], tagHash[:]...)
}
