![muun](https://muun.com/images/github-banner-v2.png)

## About

This is the source code repository for muun's wallet core library. Muun is a non-custodial 2-of-2 multisig wallet with a special focus on security and ease of use.

This library is used by our mobile wallets with [gomobile](https://godoc.org/golang.org/x/mobile/cmd/gomobile) and by the [recovery tool](https://github.com/muun/recovery).

## Setup

1. Install [golang](https://golang.org/)
2. Install [docker](https://docs.docker.com/engine/install/)
3. Run `librs/makelibs.sh`
4. Run `go build -mod=vendor .`

## Responsible Disclosure

Send us an email to report any security related bugs or vulnerabilities at [security@muun.com](mailto:security@muun.com).

You can encrypt your email message using our public PGP key.

Public key fingerprint: `2576 FE1C 2FD3 9DD0 7CAA 3D1D A6F4 476C E444 C8BE`

