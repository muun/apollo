module github.com/muun/libwallet

go 1.14

require (
	github.com/btcsuite/btcd v0.20.1-beta.0.20200515232429-9f0179fd2c46
	github.com/btcsuite/btcutil v1.0.2
	github.com/fiatjaf/go-lnurl v1.3.1
	github.com/golang/protobuf v1.4.2
	github.com/jinzhu/gorm v1.9.16
	github.com/lightningnetwork/lightning-onion v1.0.1
	github.com/lightningnetwork/lnd v0.10.4-beta
	github.com/miekg/dns v1.1.29 // indirect
	github.com/pdfcpu/pdfcpu v0.3.11
	github.com/pkg/errors v0.9.1
	golang.org/x/crypto v0.0.0-20200622213623-75b288015ac9
	golang.org/x/mobile v0.0.0-20210220033013-bdb1ca9a1e08 // indirect
	golang.org/x/net v0.0.0-20201021035429-f5854403a974 // indirect
	golang.org/x/sync v0.0.0-20201020160332-67f06af15bc9 // indirect
	golang.org/x/sys v0.0.0-20210412220455-f1c623a9e750 // indirect
	golang.org/x/xerrors v0.0.0-20200804184101-5ec99f83aff1 // indirect
	google.golang.org/protobuf v1.25.0
	gopkg.in/gormigrate.v1 v1.6.0
)

// Fork that includes the -cache flag for quicker builds
replace golang.org/x/mobile => github.com/champo/mobile v0.0.0-20210412201235-a784c99e2a62
