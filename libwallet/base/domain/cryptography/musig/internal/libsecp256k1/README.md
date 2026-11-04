# libsecp256k1

Go bindings to [libsecp256k1](https://github.com/BlockstreamResearch/secp256k1-zkp), covering the
MuSig2, Schnorr and key handling primitives `libwallet/base/domain/cryptography/musig` signs with. The
C sources live under
`upstream/`, copied verbatim by `import.sh`.

## Why secp256k1-zkp and not bitcoin-core/secp256k1

MuSig2 was upstreamed into `bitcoin-core/secp256k1`, but its module has no adaptor signatures.
`secp256k1-zkp` carries the same BIP327 implementation plus `musig_nonce_parity`, `musig_adapt` and
`musig_extract_adaptor`, which is what we need them for.

## How the C is compiled

cgo compiles the `.c` files that sit in this directory and ignores subdirectories, so
`libsecp256k1.c` pulls in the three upstream translation units:

```c
#include "upstream/src/secp256k1.c"
#include "upstream/src/precomputed_ecmult.c"
#include "upstream/src/precomputed_ecmult_gen.c"
```

Quoted includes resolve relative to the file holding the directive, so nothing under `upstream/` is
rewritten and the tree stays diffable against the fork. The build flags — which modules are enabled
and `ECMULT_WINDOW_SIZE` — are the `#cgo CFLAGS` lines in `libsecp256k1.go`.

## Updating the vendored library

```bash
./import.sh [ref]     # ref defaults to the commit pinned in the script
```

The script clones the ref, copies `include/` and `src/` verbatim, and prunes what is never
compiled: modules other than `extrakeys`, `schnorrsig` and `musig`, their public headers, assembly,
tests, test vectors, benchmarks, the table generators and the upstream build system. It records
what it took in `upstream/VERSION`.

The fork publishes no releases, so `ref` is a commit rather than a tag. Nothing runs `import.sh`
automatically: updating is a deliberate act, and the diff it produces is meant to be reviewed. To
check the tree against the fork, re-run the import and confirm it comes back clean.

Anything under `libwallet/base/domain/cryptography/musig` moves the taproot4j build id, so run
`toolkit/bin/muun build taproot4j-go` and commit the regenerated artifacts along with the change.

## Conventions in this package

* Arguments and results are plain byte slices; the callers in
  `libwallet/base/domain/cryptography/musig` own the conversion to and from `btcec` types.
* Every exported function checks the length of what it is given. These are the last checks before C
  reads a fixed number of bytes from a pointer, so they are memory safety rather than validation.
* Go memory handed to C as an array of pointers is pinned with `runtime.Pinner` rather than copied
  into C memory with `malloc`.
* libsecp256k1 aborts the process on an illegal argument or an internal error. `panic_callbacks.go`
  turns both into Go panics carrying the library's message.
