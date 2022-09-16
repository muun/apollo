module github.com/muun/libwallet

go 1.14

require (
	github.com/aead/chacha20 v0.0.0-20180709150244-8b13a72661da // indirect
	github.com/btcsuite/btcd v0.20.1-beta.0.20200515232429-9f0179fd2c46
	github.com/btcsuite/btcutil v1.0.2
	github.com/fiatjaf/go-lnurl v1.3.1
	github.com/google/uuid v1.1.1
	github.com/jinzhu/gorm v1.9.16
	github.com/lightningnetwork/lightning-onion v1.0.1
	github.com/lightningnetwork/lnd v0.10.4-beta
	github.com/miekg/dns v1.1.29 // indirect
	github.com/pdfcpu/pdfcpu v0.3.11
	github.com/pkg/errors v0.9.1
	github.com/shopspring/decimal v1.2.0
	golang.org/x/crypto v0.0.0-20210921155107-089bfa567519
	golang.org/x/mobile v0.0.0-20220414153400-ce6a79cf6a13 // indirect
	golang.org/x/sys v0.0.0-20220503163025-988cb79eb6c6 // indirect
	golang.org/x/tools v0.1.10 // indirect
	golang.org/x/xerrors v0.0.0-20200804184101-5ec99f83aff1 // indirect
	google.golang.org/protobuf v1.25.0
	gopkg.in/gormigrate.v1 v1.6.0
)

// Fork that includes the -cache flag for quicker builds
replace golang.org/x/mobile => github.com/muun/mobile v0.0.0-20220913162405-8cc629edd37b
