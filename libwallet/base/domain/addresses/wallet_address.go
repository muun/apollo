package addresses

const (
	V7                      = 7
	V8                      = 8
	V9                      = 9
	ToServer                = 301
	ToServerHTLC            = 302
	ToClientHTLC            = 303
	ToClientWithdrawal      = 304
	ToServerImmediately     = 305
	ToServerHTLCWithdrawal  = 306
	ToClientRevocable       = 307
	ToClientHTLCEnforcement = 308
	ToServerHTLCEnforcement = 309
)

type WalletAddress struct {
	version        int
	derivationPath string
	address        string
}

func New(version int, derivationPath string, address string) *WalletAddress {
	return &WalletAddress{
		version:        version,
		derivationPath: derivationPath,
		address:        address,
	}
}

func (a *WalletAddress) Version() int {
	return a.version
}

func (a *WalletAddress) DerivationPath() string {
	return a.derivationPath
}

func (a *WalletAddress) Address() string {
	return a.address
}
