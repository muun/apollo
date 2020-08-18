package libwallet

type mockSubmarineSwap struct {
	receiver      SubmarineSwapReceiver
	fundingOutput SubmarineSwapFundingOutput
}

func (m *mockSubmarineSwap) Invoice() string {
	return ""
}

func (m *mockSubmarineSwap) Receiver() SubmarineSwapReceiver {
	return m.receiver
}

func (m *mockSubmarineSwap) FundingOutput() SubmarineSwapFundingOutput {
	return m.fundingOutput
}

func (m *mockSubmarineSwap) PreimageInHex() string {
	return ""
}

type mockSubmarineSwapReceiver struct {
	alias     string
	publicKey string
}

func (m *mockSubmarineSwapReceiver) Alias() string {
	return m.alias
}

func (m *mockSubmarineSwapReceiver) PublicKey() string {
	return m.publicKey
}

type mockSubmarineSwapFundingOutput struct {
	outputAddress          string
	serverPaymentHashInHex string
	expirationInBlocks     int64
	userPublicKey          string
	muunPublicKey          string
}

func (m *mockSubmarineSwapFundingOutput) ScriptVersion() int64 { return 0 }
func (m *mockSubmarineSwapFundingOutput) OutputAddress() string {
	return m.outputAddress
}

func (m *mockSubmarineSwapFundingOutput) OutputAmount() int64      { return 0 }
func (m *mockSubmarineSwapFundingOutput) ConfirmationsNeeded() int { return 0 }
func (m *mockSubmarineSwapFundingOutput) ServerPaymentHashInHex() string {
	return m.serverPaymentHashInHex
}
func (m *mockSubmarineSwapFundingOutput) ServerPublicKeyInHex() string { return "" }
func (m *mockSubmarineSwapFundingOutput) UserLockTime() int64          { return 0 }

func (m *mockSubmarineSwapFundingOutput) UserRefundAddress() MuunAddress {
	addr, _ := newMuunAddress(AddressVersion(addressV4), m.UserPublicKey(), m.MuunPublicKey())
	return addr
}

func (m *mockSubmarineSwapFundingOutput) ExpirationInBlocks() int64 { return m.expirationInBlocks }
func (m *mockSubmarineSwapFundingOutput) UserPublicKey() *HDPublicKey {
	key, _ := NewHDPublicKeyFromString(m.userPublicKey, "m", Regtest())
	return key
}

func (m *mockSubmarineSwapFundingOutput) MuunPublicKey() *HDPublicKey {
	key, _ := NewHDPublicKeyFromString(m.muunPublicKey, "m", Regtest())
	return key
}
