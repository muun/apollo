module github.com/muun/libwallet

go 1.14

require (
	github.com/btcsuite/btcd v0.20.1-beta.0.20200515232429-9f0179fd2c46
	github.com/btcsuite/btcutil v1.0.2
	github.com/golang/protobuf v1.4.2
	github.com/jinzhu/gorm v1.9.16
	github.com/lightningnetwork/lightning-onion v1.0.1
	github.com/lightningnetwork/lnd v0.10.4-beta
	github.com/miekg/dns v1.1.29 // indirect
	github.com/pdfcpu/pdfcpu v0.3.8
	github.com/pkg/errors v0.9.1
	golang.org/x/crypto v0.0.0-20200510223506-06a226fb4e37
	golang.org/x/exp v0.0.0-20190731235908-ec7cb31e5a56 // indirect
	golang.org/x/sys v0.0.0-20200413165638-669c56c373c4 // indirect
	google.golang.org/protobuf v1.25.0
	gopkg.in/gormigrate.v1 v1.6.0
)

// Fork that includes the -cache flag for quicker builds
replace golang.org/x/mobile => github.com/champo/mobile v0.0.0-20201226003606-ef8e5756cda7
