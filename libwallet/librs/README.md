# What is this?

`librs` is a set of bindings and glue so that libwallet can verify Zero-Knowledge proofs written 
in Rust. There's little to none mature libraries for dealing with ZK without a trusted setup in
go, making this necessary.

# Structure

The actual proof code is in `prover/libs`. Most of the files here are just glue to expose the
verifier code. 

* `generate` is a Rust script to precompute the proof circuit. This precomputation is independent
  of any inputs and is computationally expensive, so we run it during compile time and store it in
  `bindings/src/bin`.
* `bindings` are the rust to C bindings for the proof code in `prover/libs`.
* `Cargo.toml` is the workspace file for `generate` and `bindings`
* `makelibs.sh` runs the circuit precomputation and builds the bindings for all target architectures
* `libs.Dockerfile` is used by `makelibs.sh` to build the rust projects reproducibly
* `libs` contains compiled bindings
* `librs.go` are the C to golang bindings, using CGO to link against `libs`

# Targets

We support Android, iOS, macOS and Linux targets. All targets are enumerated in `makelibs.sh`.

# Building

You need docker installed. To build for iOS or macOS the host needs to be macOS.

```bash
./makelibs.sh
```

# Gotchas

Libwallet is meant to be compiled via `gomobile` for usage in the mobile apps. However, for iOS
`gomobile` has a bug that doesn't link the librs libs declared in `librs.go`. It does, however,
happily build an incomplete framework. The solution is that `makelibs.sh` also generates a 
`.xcframework` file with the bindings that can be included in the Xcode project for iOS.
