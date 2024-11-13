# MuSig2 v0.4.0

This package contains a modified copy of the MuSig2 code as found in
`github.com/btcsuite/btcec/v2/schnorr/musig2` at the tag `btcec/v2.2.2` commit
`a2cbce3dbcfaf696ceb057147352ca61f1f968ec`. If you are planning to reproduce
those modifications, the patch in this directory may become handy.

The nature of the modifications is to adapt the behavior to what
`secp256k1-zkp` does. The nonces are generated only by providing a sessionId
as rand parameter. Leaving the rest null, the serialization of the preimage of
the nonce also varies.

This corresponds to the [MuSig2 BIP specification version of
`v0.4.0`](https://github.com/jonasnick/bips/blob/musig2/bip-musig2.mediawiki).

We only keep this code here to allow implementing a backward compatible,
versioned MuSig2 RPC.
