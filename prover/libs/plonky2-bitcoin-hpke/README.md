# plonky2-bitcoin-hpke

A plonky2 circuit for HPKE in base mode, single shot with the following configuration:
- KEM = DHKEM(secp256k1, HKDF-SHA256)
- KDF = HKDF-SHA256
- AEAD = ChaCha20Poly1305

The implementation is as described in [RFC 9180](https://datatracker.ietf.org/doc/rfc9180/), following the proposed [DHKEM(secp256k1, HKDF-SHA256)](https://www.ietf.org/archive/id/draft-wahby-cfrg-hpke-kem-secp256k1-01.html) extension.

This implementation also agrees with the [bitcoin-hpke](https://crates.io/crates/bitcoin-hpke) crate.
