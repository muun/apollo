package libwallet

// Tests matching PartiallySignedTransaction_Sign* are generated using the mobile
// app directly or Houston's OperationTest::generateTestForLibwallet.
// those tests will ensure that the backend is creating a valid partially signed
// transaction matching the intent of our inputs

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/test-go/testify/require"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/walletdb"
)

const (
	basePath = "m/schema:1'/recovery:1'"
)

type input struct {
	outpoint        outpoint
	address         MuunAddress
	userSignature   []byte
	muunSignature   []byte
	submarineSwapV1 inputSubmarineSwapV1
	submarineSwapV2 inputSubmarineSwapV2
	incomingSwap    inputIncomingSwap
	muunPublicNonce []byte
}

func (i *input) OutPoint() Outpoint {
	return &i.outpoint
}

func (i *input) Address() MuunAddress {
	return i.address
}

func (i *input) UserSignature() []byte {
	return i.userSignature
}

func (i *input) MuunSignature() []byte {
	return i.muunSignature
}

func (i *input) SubmarineSwapV1() InputSubmarineSwapV1 {
	return &i.submarineSwapV1
}

func (i *input) SubmarineSwapV2() InputSubmarineSwapV2 {
	return &i.submarineSwapV2
}

func (i *input) IncomingSwap() InputIncomingSwap {
	return &i.incomingSwap
}

func (i *input) MuunPublicNonce() []byte {
	return i.muunPublicNonce
}

type outpoint struct {
	txId   []byte
	index  int
	amount int64
}

func (o *outpoint) TxId() []byte {
	return o.txId
}

func (o *outpoint) Index() int {
	return o.index
}

func (o *outpoint) Amount() int64 {
	return o.amount
}

type inputSubmarineSwapV1 struct {
	refundAddress   string
	paymentHash256  []byte
	serverPublicKey []byte
	lockTime        int64
}

func (i *inputSubmarineSwapV1) RefundAddress() string {
	return i.refundAddress
}

func (i *inputSubmarineSwapV1) PaymentHash256() []byte {
	return i.paymentHash256
}

func (i *inputSubmarineSwapV1) ServerPublicKey() []byte {
	return i.serverPublicKey
}

func (i *inputSubmarineSwapV1) LockTime() int64 {
	return i.lockTime
}

type inputSubmarineSwapV2 struct {
	paymentHash256      []byte
	serverPublicKey     []byte
	userPublicKey       []byte
	muunPublicKey       []byte
	blocksForExpiration int64
	serverSignature     []byte
}

func (i *inputSubmarineSwapV2) PaymentHash256() []byte {
	return i.paymentHash256
}

func (i *inputSubmarineSwapV2) ServerPublicKey() []byte {
	return i.serverPublicKey
}

func (i *inputSubmarineSwapV2) UserPublicKey() []byte {
	return i.userPublicKey
}

func (i *inputSubmarineSwapV2) MuunPublicKey() []byte {
	return i.muunPublicKey
}

func (i *inputSubmarineSwapV2) BlocksForExpiration() int64 {
	return i.blocksForExpiration
}

func (i *inputSubmarineSwapV2) ServerSignature() []byte {
	return i.serverSignature
}

type inputIncomingSwap struct {
	sphinx              []byte
	htlcTx              []byte
	paymentHash         []byte
	swapServerPublicKey string
	expirationHeight    int64
	collectInSats       int64
	preimage            []byte
	htlcOutputKeyPath   string
}

func (i *inputIncomingSwap) Preimage() []byte {
	return i.preimage
}

func (i *inputIncomingSwap) HtlcOutputKeyPath() string {
	return i.htlcOutputKeyPath
}

func (i *inputIncomingSwap) Sphinx() []byte {
	return i.sphinx
}

func (i *inputIncomingSwap) HtlcTx() []byte {
	return i.htlcTx
}

func (i *inputIncomingSwap) PaymentHash256() []byte {
	return i.paymentHash
}

func (i *inputIncomingSwap) SwapServerPublicKey() string {
	return i.swapServerPublicKey
}

func (i *inputIncomingSwap) ExpirationHeight() int64 {
	return i.expirationHeight
}

func (i *inputIncomingSwap) CollectInSats() int64 {
	return i.collectInSats
}

// generates test nonces using fixed sessionIds and a list of inputs
func createTestNonces(t *testing.T, userKey *HDPrivateKey, inputList *InputList, userSessionIds []string) *MusigNonces {
	nonces := EmptyMusigNonces()
	for index := range inputList.inputs {
		input := inputList.inputs[index]
		inputSignerKey, err := userKey.DeriveTo(input.Address().DerivationPath())
		require.NoError(t, err)

		var sessionId [32]byte
		copy(sessionId[:], hexToBytes(userSessionIds[index]))

		nonces.generateStaticNonce(
			input.Address().Version(),
			inputSignerKey.PublicKey().Raw(),
			sessionId,
		)
	}
	return nonces
}

func TestPartiallySignedTransaction_SignV1(t *testing.T) {
	const (
		hexTx    = "0100000001706bcabdcdcfd519bdb4534f8ace9f8a3cd614e7b00f074cce0a58913eadfffb0100000000ffffffff022cf46905000000001976a914072b22dfb34153d4e084dce8c6655430d37f12d088aca4de8b00000000001976a914fded0987447ef3273cde87bf8b65a11d1fd9caca88ac00000000"
		hexTxOut = "fbffad3e91580ace4c070fb0e714d63c8a9fce8a4f53b4bd19d5cfcdbdca6b70"
		txIndex  = 1
		txAmount = 100000000

		addressPath   = "m/schema:1'/recovery:1'/external:1/1"
		originAddress = "n4fbDDpmfZgyjHsp93C5z7rd68Wq5kS2tj"

		encodedUserKey = "tprv8eJiUjHpVRyTUM1p4XDRUdRZPJLfud22swAv48my1MxaCZztUNRrWxmN6ycdd9a2xfJwLchq5jW9m2jkNpwruijwvygCv41e6YrsqUvw7hQ"
	)

	txOut1, _ := hex.DecodeString(hexTxOut)

	inputs := []Input{
		&input{
			outpoint: outpoint{index: txIndex, amount: txAmount, txId: txOut1},
			address:  addresses.New(addresses.V1, addressPath, originAddress),
		},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)
	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	// We dont need to use the muunKey in V1
	signedRawTx, err := partial.Sign(userKey, userKey.PublicKey())

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, hexTx, txIndex, 0)

}
func TestPartiallySignedTransaction_SignV2(t *testing.T) {

	const (
		hexTx = "0100000004f3c15d23060a622bef5e0346ba3410ec118b959be0058c282a1e2045af511b720100000000ffffffffb8ac53a0702e45f7d0164cf6164b48fe66b56af23308e9478cb75e3a2627b74a0100000000ffffffff4e54dc96b07fb29f709c30007fc12abdcde6a20bcad73c8ec6124f34ce096f9b0000000000ffffffff4c11c4284a8e48baa4527fd26e7d0c3dda25ffb3a7f92aa2a248b5a76981d8a40000000000ffffffff01a9cbea0b0000000017a914dfca2abd2bb72cf911940a9d16de126cc1cd60368700000000"

		txIndex1    = 1
		txAmount1   = 50000000
		hexTxOut1   = "721b51af45201e2a288c05e09b958b11ec1034ba46035eef2b620a06235dc1f3"
		hexMuunSig1 = "3045022100d07028674c49d8dabc536db47f1371c2f61fc578cb2c8797a570e3176f5e91c902206a83db8ad5b63e88c48d0ae4e67646fcf6e33d0177a88996c15b280494885e7b01"
		hexTx1      = "0200000001020678c852c6d943cf0d3a9b5102b1a4e2ebccdb4ca2eaae7731c8f59b81172a000000004847304402204a3958c1bd6abcd7b5ec2291bd43391dcfe757068ff0e340dd8f502cb25435b0022076e865730e49e4d126b94675d276545e35afa84feea2873bb5f923b842d90f4801feffffff0224bf45220000000017a914cb81f4e1ff68249e6f4f17a7995007b5a478705b8780f0fa020000000017a914dfca2abd2bb72cf911940a9d16de126cc1cd60368794020000"

		txIndex2    = 1
		txAmount2   = 50000000
		hexTxOut2   = "4ab727263a5eb78c47e90833f26ab566fe484b16f64c16d0f7452e70a053acb8"
		hexMuunSig2 = "304402201b0c35179a5fa8e6255115450979a77dbb97d89157e236783df0312a5d7bdb2c022064bae7ad0cdc72e4339421067cc65e0c3d03690a5c2d98c32a6ef67f883558a001"
		hexTx2      = "0200000001ff3f3b16506ef957b9ea80287f276ee415380597a4ede7ae45fff6e18d3e13d8000000004847304402204dbe876d7f0761a72ecc2d0e0e45c1ab32d6bd69d5062068984e26af02c4b27102202f2bd18a17821bdce155b13ea2c379bb78c9157f7f44e2e6a8cef1a154ec68ac01feffffff0224bf45220000000017a914684830d4ef58c54b6b3db6b4a3eb7818d418ae258780f0fa020000000017a914dfca2abd2bb72cf911940a9d16de126cc1cd60368794020000"

		txIndex3    = 0
		txAmount3   = 50000000
		hexTxOut3   = "9b6f09ce344f12c68e3cd7ca0ba2e6cdbd2ac17f00309c709fb27fb096dc544e"
		hexMuunSig3 = "30440220076b14b1c906089546cb40ce05dab38f0388ca65d0bc5183d3c3f7dcb98be52c022001eea4635d56726d990daa92ac26c52c9030c96dddcc92e5d623546580aaaef401"
		hexTx3      = "02000000019fdde3b7eb40584d103a04dd253ffa0ceb458776db56fbee6489aee0d34402d6000000004847304402206abfb750561acac1be3d6ec3eabc1c88ac7ce11f28f5c8162428ce78dabb4d8e0220753c03bf8b9af9c9bf592f52586d39d8aa10c1111f105fea0ce0cf5c82a4574101feffffff0280f0fa020000000017a914dfca2abd2bb72cf911940a9d16de126cc1cd60368724bf45220000000017a9148d7814264268f1f0f98870f95dc69017bd0cce708794020000"

		txIndex4    = 0
		txAmount4   = 50000000
		hexTxOut4   = "a4d88169a7b548a2a22af9a7b3ff25da3d0c7d6ed27f52a4ba488e4a28c4114c"
		hexMuunSig4 = "30440220145dcce0bf6cceda98b3a9635bd7611d92085ff3ad27690bcf471a6b39620e6c02205ca0a0bd93550e86468e236b291457a3ff84a3b5dedeb10067cc9d3233b5dafa01"
		hexTx4      = "02000000019d657207178c19bb4fd45de6a5f83caadf86bd7519e1569c8daf078a46e565310000000048473044022033c864f4a6ab42ba29d09bb2dd110e55a3c4118fd0a68cbe5c461926cc64d3e9022029a5b57a2a6e24e6f66f4354b74d7ffc7affa6d43843797faa70c84ec47b7b8501feffffff0280f0fa020000000017a914dfca2abd2bb72cf911940a9d16de126cc1cd60368724bf45220000000017a914b392913e36a7017404c60424da4ebb48a53b5bb18794020000"

		addressPath   = "m/schema:1'/recovery:1'/external:1/0"
		originAddress = "2NDeWrsJEwvxwVnvtWzPjhDC5B2LYkFuX2s"

		encodedMuunKey = "tpubDBYMnFoxYLdMBZThTk4uARTe4kGPeEYWdKcaEzaUxt1cesetnxtTqmAxVkzDRou51emWytommyLWcF91SdF5KecA6Ja8oHK1FF7d5U2hMxX"
		encodedUserKey = "tprv8dfM4H5fYJirMai5Er3LguicgUAyxmcSQbFub5ens16amX1e1HAFiW4SXnFVw9nu9FedFQqTPGTTjPEmgfvvXMKww3UcRpFbbC4DFjbCcTb"
		basePath       = "m/schema:1'/recovery:1'"
	)

	txOut1, _ := hex.DecodeString(hexTxOut1)
	muunSig1, _ := hex.DecodeString(hexMuunSig1)
	txOut2, _ := hex.DecodeString(hexTxOut2)
	muunSig2, _ := hex.DecodeString(hexMuunSig2)
	txOut3, _ := hex.DecodeString(hexTxOut3)
	muunSig3, _ := hex.DecodeString(hexMuunSig3)
	txOut4, _ := hex.DecodeString(hexTxOut4)
	muunSig4, _ := hex.DecodeString(hexMuunSig4)

	inputs := []Input{
		&input{
			outpoint:      outpoint{index: txIndex1, amount: txAmount1, txId: txOut1},
			address:       addresses.New(addresses.V2, addressPath, originAddress),
			muunSignature: muunSig1},
		&input{
			outpoint:      outpoint{index: txIndex2, amount: txAmount2, txId: txOut2},
			address:       addresses.New(addresses.V2, addressPath, originAddress),
			muunSignature: muunSig2},
		&input{
			outpoint:      outpoint{index: txIndex3, amount: txAmount3, txId: txOut3},
			address:       addresses.New(addresses.V2, addressPath, originAddress),
			muunSignature: muunSig3},
		&input{
			outpoint:      outpoint{index: txIndex4, amount: txAmount4, txId: txOut4},
			address:       addresses.New(addresses.V2, addressPath, originAddress),
			muunSignature: muunSig4},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)
	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, hexTx1, txIndex1, 0)
	verifyInput(t, signedTx, hexTx2, txIndex2, 0)
	verifyInput(t, signedTx, hexTx3, txIndex3, 0)
	verifyInput(t, signedTx, hexTx4, txIndex4, 0)

}

func TestPartiallySignedTransaction_SignV3(t *testing.T) {
	const (
		hexTx = "01000000014a4ca718419999e9bfb675dc9f7deff6b65512c11469a23d169038267cd097040100000000ffffffff02916067590000000017a91437a2fceeb0c454b22b427c34eb565d8b1dc953ed8797c400000000000017a9142b0cabe5d058bc3c58f8a656dec2601d117262538700000000"

		txIndex1    = 1
		txAmount1   = 1500000000
		hexTxOut1   = "0497d07c263890163da26914c11255b6f6ef7d9fdc75b6bfe999994118a74c4a"
		hexMuunSig1 = "3045022100d138caf8d3c19db84363b33e1ad002e1aee7907302ab5110edaf78d980c94e48022019e841da8759f63596fbcd81a3544219573288877206f8f651cae1023c397f0c01"
		hexTx1      = "02000000014f1e7a952c72670bf03a040faa183687ec8c9e0fb7adf606d1ce13395fb663000000000017160014a89e2ded102b2dde96e8bc87219113c6d31a1fe4feffffff02240e5ea9cf00000017a9142773c1a1651ad774f4b867d955ae8b816ac806ad87002f68590000000017a9142b0cabe5d058bc3c58f8a656dec2601d117262538736010000"

		encodedMuunKey = "tpubDABPYHYrYQHXY2pYFdcsFd41aE2uZmMQZpRRGiKfgz7G7nU7PoSwrzMKeHHnoMjmn9woC87coUanF2T911R8X5HpUtZRJRf56u4r51gTrqD"
		encodedUserKey = "tprv8ezdJAiJTZz4BJo1VysKviVqto1f8CAS3d2M9LWZ5oygiMrtb6NYcPnkWTcdP8b2AuKVVegnWe3Czzo7geDqH2MzXvzDu1SiKucVAG6KFvE"

		addressPath   = "m/schema:1'/recovery:1'/external:1/0"
		originAddress = "2MwArDxm83HCWKvoLKcKAg1Nv6ZG7fWYzMa"
	)

	txOut1, _ := hex.DecodeString(hexTxOut1)
	muunSig1, _ := hex.DecodeString(hexMuunSig1)

	inputs := []Input{
		&input{
			outpoint:      outpoint{index: txIndex1, amount: txAmount1, txId: txOut1},
			address:       addresses.New(addresses.V3, addressPath, originAddress),
			muunSignature: muunSig1},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)
	nonces := GenerateMusigNonces(1)
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, hexTx1, txIndex1, 0)
}

func TestPartiallySignedTransaction_SignV5(t *testing.T) {
	var (
		encodedUserKey = "tprv8e6WDju7yhq6vuL8raFiMpCMVYpNEpjggzqcX3qW4zsjNBVnwKgAUmQ7vs7bDeHu598aG9teh7or5H8ifLJ2qhZGocBnDEBqAsTs3Gd6wG6"
		encodedMuunKey = "tpubDBS2rf9CeryjGstPrQVSzQhLGLqFVqEq78xtK26h9fsN7udiokAMuu6DbSzwhSzqCwszcfC2L2zMYoFm9uoiJpkEwyUCuNr3j1XswbHcgAB"
		hexTx          = "0100000001239fc65d1212989754b0bb146ccc77db370de8db913a5bcbb1e5257ae75e03450100000000ffffffff0174850100000000002251203e3c9519c91c87e84de71a64f65fa481639c900da4e01ba4c23be539c9065ad400000000"
		userSessionIds = []string{
			"3afcd7f2cc568aa60552866f7ee8d1de6a6b18293ae9a4cda167434588267f73",
		}
		inputTxRaw = []string{
			"020000000001019da62d921c36e14d8c8567adec86580e95154cddb922f28ed47552f1850272c10000000000feffffff028b4d042a01000000160014290cfc7b1cb593b8b7cfb70e8a5315cb04015fd7a0860100000000002251206814f05813d2505f80ec5a71ca6fbbd99df3aac5e0ed3a506a1c376beb484e9102473044022040d7a4204b14cf1ea32021040d373221f60fcfb42e4da82723b0eb3b7d7bd940022064160bd834d6b530292d9f6f17c07e032a619b88c8ffcb5ef6622a731236ba46012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
		}
	)
	inputList := &InputList{inputs: []Input{
		&input{
			outpoint:        outpoint{index: 1, amount: 100000, txId: hexToBytes("45035ee77a25e5b1cb5b3a91dbe80d37db77cc6c14bbb054979812125dc69f23")},
			address:         addresses.New(5, "m/schema:1'/recovery:1'/external:1/0", "bcrt1pdq20qkqn6fg9lq8vtfcu5mammxwl82k9urkn55r2rsmkh66gf6gsumc8uw"),
			muunPublicNonce: hexToBytes("02b44aef04d3ada7270e1304f4ba1fbf20cca0ca81a80e23be9bf8f3aea7c0a62103f6c8cd699fc2339a33df60f142bb89807c081632a2241bd4af583961a29da9a0"),
			muunSignature:   hexToBytes("591f2e7afd46a8b234e94428e582f13324cd541d436bde62cef760354771a377"),
		},
	}}

	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())

	nonces := createTestNonces(t, userKey, inputList, userSessionIds)
	partial, _ := NewPartiallySignedTransaction(inputList, hexToBytes(hexTx), nonces)
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	// validate input signatures
	for index := range inputList.inputs {
		verifyInput(t, signedTx, inputTxRaw[index], inputList.inputs[index].OutPoint().Index(), index)
	}
}

func TestPartiallySignedTransaction_SignV6(t *testing.T) {
	var (
		encodedUserKey = "tprv8dUpNFvQ6NpkxtuYDoDaibgQYbvUnHBqz8GM3zBL4DjNeh9uzhXC49xKx2VksbyxaW3dSFviExbUw4GmkEKJoiTx7UXXi6pPnXMWpB5Lmtf"
		encodedMuunKey = "tpubDBmgp5wQ4SYkroyXQG3SxUXVZsdmJnL89exWksCAEq9xzujjCd6jpKbYQyyVXLiQk4gBq8AaUULZDbwxFF8DhcTEPzDFYY8g2dsJ1x3xPwN"
		hexTx          = "01000000017e282eab04b710e5f149e6a3f7dfb8dcb5af006aefecc7e5d2ad1f4bbb4d78db0000000000ffffffff017485010000000000225120e67c60c89364bae43a399e6417a1cce9d2e0498e5eb0646f52d3f279833a2b6000000000"
		userSessionIds = []string{
			"98ca651de1178c9a656dfc51e00bb6ff3dd958922a15a84ef270a81c96bcf510",
		}
		inputTxRaw = []string{
			"02000000000101f8880208d09f48d849e68d042194b3295401a88dcc9fa5131ecc78130b7480840000000000feffffff02a086010000000000225120c6eae34415dcc0f4e40367348f483e7d6a30f969a54586fb699b5684988ddb1e8b4d042a01000000160014287cd946de86caf84c507e749f2fbe586bce57b30247304402203c30d6522a082228b6b9ee1bc93a576a46d37e17017db2ae7506c8f288ae181f02206f84ed0e17f70e8b49900450c29cfff84d321850a8e4f662216a4d77c3e80795012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855ef9040000",
		}
	)
	inputList := &InputList{inputs: []Input{
		&input{
			outpoint:        outpoint{index: 0, amount: 100000, txId: hexToBytes("db784dbb4b1fadd2e5c7ecef6a00afb5dcb8dff7a3e649f1e510b704ab2e287e")},
			address:         addresses.New(6, "m/schema:1'/recovery:1'/external:1/0", "bcrt1pcm4wx3q4mnq0feqrvu6g7jp7044rp7tf54zcd7mfndtgfxydmv0qnnmrdl"),
			muunPublicNonce: hexToBytes("03722e555ae015f5e5b07ff8915fcf9a155ad74f77e27d5908b5a6c5ea313d71db0319258976bd5f967317c61ce634cf182ead2931fc54e31cc05729014b4d210f66"),
			muunSignature:   hexToBytes("f4d1fee38aebb1d17c0dc85b7a0e48474c0b1de351dfc7f0baa6e9330765881b"),
		},
	}}

	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())

	nonces := createTestNonces(t, userKey, inputList, userSessionIds)
	partial, _ := NewPartiallySignedTransaction(inputList, hexToBytes(hexTx), nonces)
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	// validate input signatures
	for index := range inputList.inputs {
		verifyInput(t, signedTx, inputTxRaw[index], inputList.inputs[index].OutPoint().Index(), index)
	}
}

func TestPartiallySignedTransaction_SignAll(t *testing.T) {
	var (
		encodedUserKey = "tprv8dhZ55jWbg1oQHf7BkxL8AFJMWB4gZhyp2tzbHtLQd3g3L7b2MBuq3dEJMdgRevAxQ8BFSjgCRoC5jp9zpDnphGvjq8pT5Q2aA111dg5pxS"
		encodedMuunKey = "tpubDBCFedMe1hS3ba6qSTivfi2f6MieNcgms1b6b9KK1xD6wtvG82dSvYQFgQcF2MBs4kyWEp7MB8tXgYzxiYpxDBnSU2F1sxrict9bNikM9kc"
		hexTx          = "010000000676cc7d617672313b795a1cac0afe1b045aa2a56face4c61202b733fa543cb4c40000000000ffffffffb3f49f5cfcb0dbaee92be859a79ebd3120964ad3208fc178e3fec5968a194f4b0100000000ffffffff6854626a1687a052653df7630b272446e87bb8eb57a74e537d8ff116248821420100000000ffffffff51849b950f0becdc008da1d072250bd65ddeabfd4a80feed09e25bd0a9ca1e5f0100000000ffffffff7d69cb9ee91913b5d96a4f8c5a32e86a96560c4db3e40f0496772a51186bf0280100000000ffffffffe894cf036db7f07d56b003036477d5c8ed494bbd1cec8a38c355fc88609573140100000000ffffffff01b820090000000000225120a21f34d36f71805071d0e3cb9cd92b0f8267b7ac95db0d60a60d7ec6bbff12db00000000"
		userSessionIds = []string{
			"bdc72b8cb2f8278ac5de74a585c8e60729d82635c574a0621e4f086121274227",
			"edb7f1cad77fabe63751fcc30040a91c02675f8d31df424c7a0c723e2134878a",
			"cdac321cead1a473bfdf17329f6c31baf30b9f6bd5931282e3dda5f7b2c16f61",
			"e8a79000514f74990e764e185e5729ec77e17052ff903b8c3788cba3b9e151f3",
			"a71d46b2292b209147776ecc2f72d9e1d8f815e993d566fc168ccb81663155b2",
			"0250df31823ed2e8fda46e928645a23ccbafddf39f7649579807c9e49fe5b143",
		}
		inputTxRaw = []string{
			"0200000000010148823860c5e3f3db0f1bd0cc70d02f0d773c878c0a366ffcd01be6444d3d0cc00000000000feffffff02a08601000000000017a914d88e83d1a5d3238decb7d4442c24f7bb3b8da52b87b04f042a01000000160014e21f5442bce128b113a9706a0ce53cc3f43943610247304402203a316d5941a810b5aa33cc08d10a88d754285e98fd88e26b6861b5f404b0b546022017024d58a8ab1ca0c33d081c5e6ca3192f3b1ba72676731e52270a5fb74ecb58012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
			"02000000000101873c68ac3f94b583efe49c3f00ae3cc7f77c9a78bccc3fdf61920edd45545b7f0000000000feffffff02b04f042a01000000160014d66fdb6789560b3fd7b0d000da0d6f22b9c70252a08601000000000017a914b82751531d1ff4fefd7fa348b900ba66d17cdcc7870247304402204bddec0acf2a0d33f7ef89f1202c6d7d4c8151756d368cc45b1c3ed3a828e7800220245589b38c63e6f32333f14d6694fc613bd6354ae354f9309f6cd3fae677c50c012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
			"0200000000010161e13760dbe0f3fcab9ed507538890d017812655f218f9bf2e1b38e5792841f80000000000feffffff028b4d042a01000000160014b00f4fabc6dec5f23326c693d92b65573c9433daa0860100000000002200204eb582ca70159962cff162ada5d0c4f6cdfe7eef173bf4c970ad177fe69ada6402473044022012d3ae236f46c2484be4e19792fb9ce7b4f51c76f6b808000e15f8872a280c7502202195bceec3fdb1d7333badf9be8950ae3210438e5ffb2e30a1573302ddeef36a012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
			"020000000001019dcb550b07a53ec866ec2558f94d8b801ef60200378cd78afcde2f123a9d619a0000000000feffffff024d4f042a01000000160014af568af3154b649bcfd7c3bfdb814043412e817fa0860100000000001976a914320cf4affd84615973e948a47a96f9103d68f8c288ac02473044022075bf561de3708e652cc9910edfd56c0158e86d99e8b3e4e1284c1bef13e72d1802203ff834bd90443dafef0488793cc6a007946ec781d4ac0dea5947a28794dfc748012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
			"02000000000101204b88f3b89ad992a9ce63bf857d08f26ee20a0e6bb4e912ddb41f56d145078c0000000000feffffff028b4d042a01000000160014032c403be2d0a8bb0f3db634a68b31d31fb498c9a0860100000000002251205cedceb4e29b9632835293f5b68fd0de48d93420e6c461b0a1c10dc8ced91be50247304402202523ae6ed227cc7b80f34eb7662d3e90681666828b9ae437249d125dfc14dba602207a82b7ac2ae18ed0e742245e8277364b6bec00a0c7dc428085b577c9a3e98ffe012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e25050000",
			"02000000000101e33e3ed531e1956474279b70325c8ad5283706fe8bd29b1358f1e1d4d0aac8a30000000000feffffff028b4d042a01000000160014da492a8cc860dab06f59d941c2dec6e88a29b545a08601000000000022512074300b4a8179bfe6eacd7e478c506cd00445ae3dd489634684626c9b04e4ec8d02473044022037b78bd154dac8dcda7ae9f109f4612add9396731bb01ad44f58e14011f7be0f022042b5b13e03815d9ae5748d28d9e70274d9474a315e928b37c4d5a3916572c0af012103ff6f07c524f89457d05a1ee9e2b8d1ae2520916748b2da0c7c1d3655f271855e1b050000",
		}
	)
	inputList := &InputList{inputs: []Input{
		&input{
			outpoint:      outpoint{index: 0, amount: 100000, txId: hexToBytes("c4b43c54fa33b70212c6e4ac6fa5a25a041bfe0aac1c5a793b317276617dcc76")},
			address:       addresses.New(3, "m/schema:1'/recovery:1'/external:1/2", "2NCzGfq4MurQzhodoSFtB2VSEJ8Tc5MN82j"),
			muunSignature: hexToBytes("3045022100860c48eb2374dbb67ed68dd91198994407a4e933f92e4f3187706f93debf246b02206b7d0607eaad478f6323cb707344739ce0ba8583266a39b617dd0e5bb77633b501"),
		},
		&input{
			outpoint:      outpoint{index: 1, amount: 100000, txId: hexToBytes("4b4f198a96c5fee378c18f20d34a962031bd9ea759e82be9aedbb0fc5c9ff4b3")},
			address:       addresses.New(2, "m/schema:1'/recovery:1'/external:1/1", "2NA2wRRMNCsfECwvjHwAdPmciFRGrBKzc6W"),
			muunSignature: hexToBytes("3045022100c907055dd0033f4f28113f566f68a9c0c400f804d83faca36f5f77a92696bf960220379da01e954f5cdee9fc69dfdf59ac33d36b4af5ea801fd011c2911df2ace2b501"),
		},
		&input{
			outpoint:      outpoint{index: 1, amount: 100000, txId: hexToBytes("4221882416f18f7d534ea757ebb87be84624270b63f73d6552a087166a625468")},
			address:       addresses.New(4, "m/schema:1'/recovery:1'/external:1/3", "bcrt1qf66c9jnszkvk9nl3v2k6t5xy7mxlulh0zualfjts45thle56mfjqvkvgh0"),
			muunSignature: hexToBytes("3045022100bb2a22510930bc31ddb6b9245eebe5d22499c54d08b326eebd249403f9f6128b02207c10ec0fb8a336230f352b4d2ed10be91cc31d883fd545dfc5124ce8c63a01bf01"),
		},
		&input{
			outpoint: outpoint{index: 1, amount: 100000, txId: hexToBytes("5f1ecaa9d05be209edfe804afdabde5dd60b2572d0a18d00dcec0b0f959b8451")},
			address:  addresses.New(1, "m/schema:1'/recovery:1'/external:1/0", "mk5bbq9qZ6Hh6u34wxvA4Hs6cEc4AF2HfL"),
		},
		&input{
			outpoint:        outpoint{index: 1, amount: 100000, txId: hexToBytes("28f06b18512a7796040fe4b34d0c56966ae8325a8c4f6ad9b51319e99ecb697d")},
			address:         addresses.New(6, "m/schema:1'/recovery:1'/external:1/5", "bcrt1ptnkuad8znwtr9q6jj06mdr7smeydjdpqumzxrv9pcyxu3nker0js0h0dfc"),
			muunPublicNonce: hexToBytes("020129f74df468dadc7a1af0305f12a550a4792559b260f756b67277d1de8d0d6e03fb1229bf2eb848532714bf50d79770b2790c589926b3893c4697106dec17a27f"),
			muunSignature:   hexToBytes("1be2d8c9f6781372950374bf6986ea32d40473beac5329ca9fe1fe459ce07e26"),
		},
		&input{
			outpoint:        outpoint{index: 1, amount: 100000, txId: hexToBytes("1473956088fc55c3388aec1cbd4b49edc8d577640303b0567df0b76d03cf94e8")},
			address:         addresses.New(5, "m/schema:1'/recovery:1'/external:1/4", "bcrt1pwscqkj5p0xl7d6kd0ercc5rv6qzytt3a6jykx35yvfkfkp8yajxs77vg44"),
			muunPublicNonce: hexToBytes("03b844359aea6df24a8c7e1766b6cc81d86827f6a0239eef26bdd02d19d0c7fed103b5d431f46f51b73572a619355d60592cec11f16491220810070fd5150216fbcc"),
			muunSignature:   hexToBytes("ffc0c05c178fedc4ab2c4edc80dbb1a79d0ef5e6c0e3bfb7744215540c70d53c"),
		},
	}}

	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())

	nonces := createTestNonces(t, userKey, inputList, userSessionIds)
	partial, _ := NewPartiallySignedTransaction(inputList, hexToBytes(hexTx), nonces)
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	// validate input signatures
	for index := range inputList.inputs {
		verifyInput(t, signedTx, inputTxRaw[index], inputList.inputs[index].OutPoint().Index(), index)
	}
}

func TestPartiallySignedTransaction_SignSubmarineSwapV1(t *testing.T) {
	const (
		hexTx = "01000000021a608c7d6e40586806c33b3b1036fbd305c37e9d38990d912cc02de7e7cec05e0000000000fffffffff18bce10875329410641316bf7c4d984e00780174b6983080e9225dc26e5bd8c0100000000feffffff01705bc0230000000017a91470fcbc29723c85fdbf9fb5189220f279e9be4508878f030000"

		txIndex1       = 0
		txAmount1      = 599817960
		hexTxOut1      = "5ec0cee7e72dc02c910d99389d7ec305d3fb36103b3bc3066858406e7d8c601a"
		hexTx1         = "0100000006f65ae1c782a5b37795a203a8820719100b1c82f59a4aa1cf3bbcc121442636a50000000023220020f1dcb100a8f4249af53e2ef831e2164545f329a5e8cda589210c033896cd1f12fffffffff21cc482a9359d2762f0a3621eb825e4e728b848588767aecdd8f906833e578e0100000023220020f1dcb100a8f4249af53e2ef831e2164545f329a5e8cda589210c033896cd1f12ffffffff68b507462f19a913b7a6a2a6956cd1c514e66b669d50b3f6228cc21935b78b7f00000000232200203ec9de492dfda91c6d7e84a14f478b1fd6c4b3432aeb4262482133975f94e8f2fffffffff18bce10875329410641316bf7c4d984e00780174b6983080e9225dc26e5bd8c00000000232200209f60ba93792ab212523ad6e6daaefb06d3d0c14ba02ddeaa38582031578bbbd3ffffffff741c42cabd1464b5752e4050acc9d9dfa7ccb296d3847a0e7da6d90effa0d80b0000000023220020d4cf5b8c1ddaa1e2788596655df089cbe10ad33bae149160e07dd76b54e2a1e3ffffffffa609573ae63856433d80793d44d05b077b2c5ef1cc04d820de0d107303ce831b0000000023220020b90f5d2eaf489a24ec6f6d93a47536145fbae13b745fbc7ef9fc5a16d1fa2408ffffffff01e87ec0230000000017a91417c1f13d6ba17a62d6f1f784927c0d45ba22f6fa8700000000"
		txAddressPath1 = "m/schema:1'/recovery:1'/external:1/2"
		txAddress1     = "2MuQqs3e42GpYteWDGEN16TqCQDC8oGCpiV"
		txMuunSigHex1  = "3044022032b35746170883b2f46c2f14019eb95e2e7e4d800248e6a8b372e504dc48674b02202ff47b29abf8f1be8719e757cbd218a4111c214b0c1aa4bdfc7debaf1b46880f01"

		txIndex2           = 1
		txAmount2          = 18400
		hexTxOut2          = "8cbde526dc25920e0883694b178007e084d9c4f76b3141064129538710ce8bf1"
		hexTx2             = "0100000001c00ee241359fa47d45f4f08b67e37f7a31ebe996da59513dfc6c5af97a3959610100000023220020f1dcb100a8f4249af53e2ef831e2164545f329a5e8cda589210c033896cd1f12ffffffff02a064f5050000000017a914d2bf8b44779443e9a7571ab416c72cdee9e9d06e87e04700000000000017a9140c02072aee07d46ab06edb7d75d538c133ebd8c38700000000"
		txAddressPath2     = "m/schema:1'/recovery:1'/change:0/7"
		txAddress2         = "2MtLiXVbDBQdHKDAKwAL5AnsTo6LoCakjvg"
		txPaymentHashHex2  = "0634be42f7a600c0457ace25f2502e9e473b7d5f0e50172dcce25044c8538936"
		txServerPubKeyHex2 = "035560f6c13e630b4a4b58dac162d4cebd97eb7a96c7ba3636a0bece5c19c2c6dd"
		txLockTime2        = 911
		txRefundAddress2   = "n3yUtyw6xAnYNpfkbuVKPSqnGdbqsLNePr"

		encodedMuunKey = "tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8"
		encodedUserKey = "tprv8fFtghPy2BsdB8nrBZcrHSihQDb65yVJa5DfLcFdtjnRc8SQcV4d59hZAzn2auLdEom9KscWv5JAuxUG65gDYiBxwbGarcix7H2Vp8xXPnX"
	)

	txOut1, _ := hex.DecodeString(hexTxOut1)
	txOut2, _ := hex.DecodeString(hexTxOut2)

	muunSig1, _ := hex.DecodeString(txMuunSigHex1)
	paymentHash2, _ := hex.DecodeString(txPaymentHashHex2)
	serverPubKey2, _ := hex.DecodeString(txServerPubKeyHex2)

	inputs := []Input{
		&input{
			outpoint:      outpoint{index: txIndex1, amount: txAmount1, txId: txOut1},
			address:       addresses.New(addresses.V3, txAddressPath1, txAddress1),
			muunSignature: muunSig1,
		},
		&input{
			outpoint: outpoint{index: txIndex2, amount: txAmount2, txId: txOut2},
			address:  addresses.New(addresses.SubmarineSwapV1, txAddressPath2, txAddress2),
			submarineSwapV1: inputSubmarineSwapV1{
				refundAddress:   txRefundAddress2,
				paymentHash256:  paymentHash2,
				serverPublicKey: serverPubKey2,
				lockTime:        txLockTime2,
			},
		},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)
	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, hexTx1, txIndex1, 0)
	verifyInput(t, signedTx, hexTx2, txIndex2, 1)
}

func verifyInput(t *testing.T, signedTx *wire.MsgTx, hexPrevTx string, prevIndex, index int) {
	t.Helper()

	// Uncomment the next block if you need to see what the script engine outputs
	txscript.DisableLog()
	// logger := btclog.NewBackend(os.Stderr).Logger("test")
	// logger.SetLevel(btclog.LevelTrace)
	// txscript.UseLogger(logger)

	prevTx := wire.NewMsgTx(0)

	rawPrevTx, _ := hex.DecodeString(hexPrevTx)
	prevTx.Deserialize(bytes.NewReader(rawPrevTx))

	flags := txscript.ScriptBip16 | txscript.ScriptVerifyDERSignatures |
		txscript.ScriptStrictMultiSig | txscript.ScriptDiscourageUpgradableNops |
		txscript.ScriptVerifyStrictEncoding | txscript.ScriptVerifyLowS |
		txscript.ScriptVerifyWitness | txscript.ScriptVerifyCheckLockTimeVerify

	prevOutFetcher := txscript.NewCannedPrevOutputFetcher(prevTx.TxOut[prevIndex].PkScript, prevTx.TxOut[prevIndex].Value)
	vm, err := txscript.NewEngine(prevTx.TxOut[prevIndex].PkScript, signedTx, index, flags, nil, nil, prevTx.TxOut[prevIndex].Value, prevOutFetcher)
	if err != nil {
		t.Fatalf("failed to build script engine: %v", err)
	}

	if err := vm.Execute(); err != nil {
		t.Fatalf("failed to verify script: %v", err)
	}
}

func TestPartiallySignedTransaction_SignSubmarineSwapV2(t *testing.T) {
	const (
		hexTx = "010000000001010a1e9552f252c4f94dae951a3a2789263650d69de286ed4813333ac73179b4790000000023220020fc4ea5a79e0de596005a77df25fdc1d76a5bd2ca022b58260830b45dbf48005fffffffff0100000000000000001976a91476e6856729db9c3885fbd72c47bd225990eee4ad88ac03473044022038395a9846c02cc1b87655ea4679f3df127fa5f781c7db3598ee43acc65adab4022051f0f874a8c16544c4ab492b8a091b630703d742599ea17c61b2bfadb747f30e0147304402207bd5a91f032ed3d69a7999d170c696861f36991f6b54e24da4319eaf512ccac402203d3d14c42103261f605b3a870ab10b03ff8b84537575768067e41853d77d2b240187210310df0c435a58758d53821915501301581be8c18b63d5a0dab281aa7f98bcb6e67c210226048275203811ab30a61759f8271280cb754ede8c38b5c51fc662dec441511eac637c76a914f722e6b3c976eba035578a7b268de980682d60b1876375677cac6867029000b275ad76a9141528942b8aef6f523d8050ad6bab416d6199352288ac6800000000"

		txIndex2              = 0
		txAmount2             = 1000
		hexTxOut2             = "79b47931c73a331348ed86e29dd650362689273a1a95ae4df9c452f252951e0a"
		hexTx2                = "0100000001b9c3208b3cd1c687d73fec2022ac6ce057c00cf8ae060e5579107a8d99681a7f000000006a473044022042d2e34afb3b66b27641c774b467ce854cfa5d4f9a1eaa462174fa3c688208840220651fdeab3a8134c65431dba040b654d9d21f50343f82bc1870b5280eaff89fc101210209d4e395ce720f13439f4f73b0dac8433f2fa17f094c5fcdaa6965bf96ece088ffffffff02e80300000000000017a914fc7ee7c4ce68ca09559d9e8776f0455039ea18d58718ee052a010000001976a9143447bbd5107cb1572eeb8550f74e5d31a4bf5bd888ac00000000"
		txAddressPath2        = "m"
		txAddress2            = "2NGGJJARaFRcARRMDeSWQ46LwU46Z9oKNCZ"
		txPaymentHashHex2     = "cdb14d5fcf498e8785caff18940bbd713b98b4d425ab0503adb92ab08c5850e3"
		txServerPubKeyHex2    = "0226048275203811ab30a61759f8271280cb754ede8c38b5c51fc662dec441511e"
		txBlockForExpiration2 = 144
		txServerSignatureHex2 = "304402207bd5a91f032ed3d69a7999d170c696861f36991f6b54e24da4319eaf512ccac402203d3d14c42103261f605b3a870ab10b03ff8b84537575768067e41853d77d2b2401"

		encodedMuunKey = "tpubD6NzVbkrYhZ4Yg872usw1wxNYrpCsUmiG4faYMaogSFwJFX9sz8MrR6GNKg4qUDjb3KUYcC9nrUL7tQYfK441qkFP9pwsw6fb8gTW7vJjXq"
		encodedUserKey = "tprv8ZgxMBicQKsPdu1SiZiQbV4K2af648S6jf8Axu7RkgQborzWpQVRzrSvyoYWb5Rmy8VVyFBDjZobn7ZaK3Ax2hLvF9NxJ6gUWNLwgLxRav7"
	)

	txOut2, _ := hex.DecodeString(hexTxOut2)

	paymentHash2, _ := hex.DecodeString(txPaymentHashHex2)
	serverPubKey2, _ := hex.DecodeString(txServerPubKeyHex2)
	serverSignature2, _ := hex.DecodeString(txServerSignatureHex2)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, "m", Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, "m", Regtest())

	inputs := []Input{
		&input{
			outpoint: outpoint{index: txIndex2, amount: txAmount2, txId: txOut2},
			address:  addresses.New(addresses.SubmarineSwapV2, txAddressPath2, txAddress2),
			submarineSwapV2: inputSubmarineSwapV2{
				paymentHash256:      paymentHash2,
				serverPublicKey:     serverPubKey2,
				userPublicKey:       userKey.PublicKey().Raw(),
				muunPublicKey:       muunKey.Raw(),
				blocksForExpiration: txBlockForExpiration2,
				serverSignature:     serverSignature2,
			},
		},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)
	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, hexTx2, txIndex2, 0)
}

func TestPartiallySignedTransaction_SignIncomingSwap(t *testing.T) {
	const (
		hexTx = "0100000001e3d55a5423fd70679839f47ed496d61bd4d0964acfa556172c945041eddf3d400000000000ffffffff02f875000000000000220020f411b28870bf089c41f703dbc1a428d60eb7cce61a9d4fa4a5c28ead872d8551963d000000000000220020eee7f6df991fac39aa2fd8054c83ef045c9569507fe4a224c8320162c028267600000000"

		txIndex        = 0
		txAmount       = 46200
		hexTxOut       = "403ddfed4150942c1756a5cf4a96d0d41bd696d47ef439986770fd23545ad5e3"
		txAddressPath  = "m/schema:1'/recovery:1'/invoices:4/1189547938/512484821/1"
		txAddress      = "bcrt1qk3mqxrvcdddyhvyywqhwc0vftfqdqt877gt0pxtzety54z73rxsse9hyt9"
		paymentHashHex = "b0e74c22943fd1e2ee86b14fb6f6636c19649910705913f5bfc33014e0ca0fd4"
		sphinxHex      = "00035a24206be286645b5e2f81fe6d35bf26ceb70b15257f19e3b744c0ed855c3d8e60c5a8d7553d0a39dd162a50df5169f18129a737da3427095e1049c356e02bb71d9c70858bbf3936fb555c283d9015f4b85d629a24e84c61dc69d537545e4c0104a87a9ab6277083cf7cb21a56f10ed23e754adf357a638970fbcd38e985f42c44b69f1cfaac8dbf711a5b8edf56383d56ad4cafb297025fad5f9c3e79dad7d1342cabde86ea85950bb80237d95b676939461aed0447b88d0010023d653abf498780f7d8f9a1e5784638c893caefe95e23a85285b636ce2af87613c275e61da65255dba4f4bacde8d6efd1c29a4f8e3efb98a3881e280b8613d45dbe38b7b895621850322be927a6beb6aa183c9a11dbf29da8a3d2f6b0b6b8c7e1c62d4926f3dc1e06ead192daba315fbcd5edd2a7d08bcfa50d1b2cc799e98b3415202e7cd91ff54962e4e1d5716c339718ebea926db6e24aacf35ebc362aafcfde4d6264d56a6edf430ae4ade75cdf9c121c3708211407d5c7ad23e2bc8dfe0d71e588b01d2ada797830315ba616f6c79030481dde1d8aa1a37676aaa9a48aec1dcc535daf2547cff2d43c58acd7e09473c46cada1112e82b0502d057ce6a8a629836ea293be93c5d228169c46d0643378e20429ba09e0a236f8dc56a23e9d38509a72e3bb115dc7c959af913b7d561d17fd3df8d490e2d91c4ae16429a3ecfa45212dbd6ccd80d4ac5956ade21c46b4960c08570e0af69ee39d1c23b194f20bc4d5d5cb5ec0b1e3a376a51700d166dce2b09a6e0c2285af7d36c4d0178a1acad0bfd8d913c44506987df6406ad7f134927c5d46be261cc4025f6310e8cb8284f03ae75a75d4ca43ba1d578b1be69d76503370f95eb98a769eaf1e7c3032907f9ab50c450b7177a804b2e9cb8c5a6fe5bbeb07d0ad176961f817119989f090ad162cae302242651c0c69e7b52c36665be795538afc3aef77a1cd0a36a170d572f56eb79e07ee6544da446798b5e0a0ed92cd205288824335b0444e5eda4347d28be9b2a128d50f983c5b16ea2792eda5d352b609a08a15268e758e024dbde13ae42008c03c608c6bb1971c9eb7a1842129b056b9c0690a88c1aea43f9bc20d8e132575a1eae77ba2fe24ee780f42e6b73b7022049518f1c231fd4fe3e91ca443980e52507ebc97f8fc49036c6e141c0e74603ac02814aa0928381228f7aadd798dbaac3603099e94224dd0b51466d392d276f19b990e8b351b73d3e284fc24f1c1ee0bffad64d9415ae8ab358b01dbc7eedfadb181ab8080d0f9c151c445419ee670e8376a112a631c5ec4092aca077086299e406ab304c7f864da801147b0e09b9f8873c85e952550b62684ca9217d1c0763930b019871787b07cabe0ef8b541c2375bc7ceb4ec1153e6f8a48371f7f80c69dcd37a7d53053fde41f87231abdbed68f195ddc6082b9d0e55207fea2c4d0c8045d44bfe55de2fb71ed75f12c0105a2ea480678b73100a943c45b6d2d5556ece82f02bd12d8785f38ac96bba1167b27b40dbd4d1677cf0b96f9311382cc110f739ffba634fa5163c9e1bd6f0279356aeab301eca2398525ec136d9d3aab4634a1fe14b365c8ad4f98e217cdba327945dfe69f3a3b7f8cd932bd761b2b264a371b104559d0a69c8e7ac053512f3e7fd2cf64278f33f7288958042b3166ff0f05e174378c80ab8d01332e862f17e8cd5c74b3de9acb51e9526d8d3305fa51b447fcb289b26c96ff06e6d50a639514491077c9f70757b74c7e26800688a05274fdcf2697e69455742126dc0df95512e478417db81440a5d2f8c671df00d0bafca53a8ed4bf6c8ea0a4c8af39c7bb1103b828547b669b75f780d9d78ddd811dd1a639576b19805088c33e6e7855cc360827516f4de8f4788bf81feb45d6d31ba5277bca2c86d84f8a"
		htlcTxHex      = "020000000001042d54f0cbba265e7f2a0873ce9e03879b706b361d3ee18e8d4a29300c0948bee50000000017160014a411cc351bd7c5572a6ba5ba16e3f3f92106425bfeffffff330565534d05b1d0d4b8a1878f1a5c41d3f571f793c528a44d72af276a259bbd000000001716001443891b3727c96d1e1e91aec69b167123a429ed32feffffff4bb7cba13999b98510769270d161f2a8e0f80f1033c72579a78c105ab2b30ebf00000000171600141c9e61f3951956b00c6f215e75e7fb9a5d93988ffefffffffaa153f428dc6c0aa0be74fa56d6563556b4286d5f9b8a431d5acf0b08f00240000000001716001488ef3cc57c509e843387a7c74e55fdc27fe962f2feffffff0178b4000000000000220020b476030d986b5a4bb084702eec3d895a40d02cfef216f09962cac94a8bd119a102473044022059247039c8e3e95d2eb289e35f81b67e5811d5a757b2117cc9dab1cebf830b2e02204d15406ea075d3d4973344b9bf7c151bb39a7e2876551b8500057dce4591fb870121033d8377beee8caf5fccd958bfacfdd3b5dc1948dfad9fb09768d11e69abb76f3402473044022042e242e0c3adca4f8b3a19cbd8eefd80bf8a43debbe51c5759b2f607b7332bc6022020be1288b8dcddfdc1569c766490e8c0ea668ea1bc122d40b59cbe6589e6938701210243b39e7a2d42937b5aecf14d27b200f7486397d92cbb166ae9f3c11259caf9c602473044022024b16f6042b9a664a46ab0c8cfc3424f8a100309f4cb51e9969a62a257255e2b022053f09e518d2ba73f74d7f3feca98df2b38eb45870325a433457277caf1ce7e6a012103f0386dfb783fc1b55d50aa089326dc008fe2fa963b44d3851bc307bbc419d1c4024730440220346c877b9e983a20fbc52fca2132717e2647bba5489e4127e8032c263252c7d502202cea58c807f60c4b6e10262a2eb59daa8bf637465a4ad67db83c59768088bfb70121020dd5d5044667ebd71f0917a6182435afeb408b4142932c9f5d852b5623ca5d6800000000"

		preimageHex      = "D7EA6B6FE58119AA061CBA3A3C1B556DE966053EE0B8A455A2FA5BA6EAE978FA"
		paymentSecretHex = "E06E5076678201F6B1324421315E16B093D7E24CC6F3D76F5A900D6D5DB6313A"
		shortChanId      = uint64(17665301721646554283)

		encodedMuunKey = "tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8"
		encodedUserKey = "tprv8deMke4d4jbc5wVYMaDpoqsXYuEPvwLPN43iRRwdZqVJCr9Wc9xh5194mMJeLTkLfQHS5CgkuXbZ9uwK9Eogcx2t7JoscYtrFirGsc3kgCr"

		muunSigHex = "3045022100c4bef5d32c5ed3530cd258df645dfb0298744dee7820095aca1a188a3b2138c102201669e21db8ee4d2b090cbb18e3e52bce40fc5e07be73c1be4d26c9f13c02e69701"
	)

	txOut, _ := hex.DecodeString(hexTxOut)

	paymentHash, _ := hex.DecodeString(paymentHashHex)
	sphinx, _ := hex.DecodeString(sphinxHex)
	htlcTx, _ := hex.DecodeString(htlcTxHex)

	preimage, _ := hex.DecodeString(preimageHex)
	paymentSecret, _ := hex.DecodeString(paymentSecretHex)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, "m/schema:1'/recovery:1'", Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, "m/schema:1'/recovery:1'", Regtest())

	muunSig, _ := hex.DecodeString(muunSigHex)

	inputs := []Input{
		&input{
			outpoint:      outpoint{index: txIndex, amount: txAmount, txId: txOut},
			address:       addresses.New(addresses.IncomingSwap, txAddressPath, txAddress),
			muunSignature: muunSig,
			incomingSwap: inputIncomingSwap{
				sphinx:              sphinx,
				htlcTx:              htlcTx,
				paymentHash:         paymentHash,
				swapServerPublicKey: "03912b4cfbd725133cbc319b444c1dad96a8bb0fcf840adc28c8e05e84ecbaa89b",
				expirationHeight:    5528,
				collectInSats:       0,
			},
		},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)

	setup()

	db, _ := openDB()
	db.CreateInvoice(&walletdb.Invoice{
		Preimage:      preimage,
		PaymentHash:   paymentHash,
		PaymentSecret: paymentSecret,
		KeyPath:       "m/schema:1'/recovery:1'/invoices:4/1189547938/512484821",
		ShortChanId:   shortChanId,
		AmountSat:     txAmount,
		State:         walletdb.InvoiceStateUsed,
	})

	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, htlcTxHex, txIndex, 0)
}

func TestPartiallySignedTransaction_SignIncomingSwapCollaboratively(t *testing.T) {
	const (
		hexTx = "0100000001e3d55a5423fd70679839f47ed496d61bd4d0964acfa556172c945041eddf3d400000000000ffffffff02f875000000000000220020f411b28870bf089c41f703dbc1a428d60eb7cce61a9d4fa4a5c28ead872d8551963d000000000000220020eee7f6df991fac39aa2fd8054c83ef045c9569507fe4a224c8320162c028267600000000"

		txIndex        = 0
		txAmount       = 46200
		hexTxOut       = "403ddfed4150942c1756a5cf4a96d0d41bd696d47ef439986770fd23545ad5e3"
		txAddressPath  = "m/schema:1'/recovery:1'/invoices:4/1189547938/512484821/1"
		txAddress      = "bcrt1qk3mqxrvcdddyhvyywqhwc0vftfqdqt877gt0pxtzety54z73rxsse9hyt9"
		paymentHashHex = "b0e74c22943fd1e2ee86b14fb6f6636c19649910705913f5bfc33014e0ca0fd4"
		sphinxHex      = "00035a24206be286645b5e2f81fe6d35bf26ceb70b15257f19e3b744c0ed855c3d8e60c5a8d7553d0a39dd162a50df5169f18129a737da3427095e1049c356e02bb71d9c70858bbf3936fb555c283d9015f4b85d629a24e84c61dc69d537545e4c0104a87a9ab6277083cf7cb21a56f10ed23e754adf357a638970fbcd38e985f42c44b69f1cfaac8dbf711a5b8edf56383d56ad4cafb297025fad5f9c3e79dad7d1342cabde86ea85950bb80237d95b676939461aed0447b88d0010023d653abf498780f7d8f9a1e5784638c893caefe95e23a85285b636ce2af87613c275e61da65255dba4f4bacde8d6efd1c29a4f8e3efb98a3881e280b8613d45dbe38b7b895621850322be927a6beb6aa183c9a11dbf29da8a3d2f6b0b6b8c7e1c62d4926f3dc1e06ead192daba315fbcd5edd2a7d08bcfa50d1b2cc799e98b3415202e7cd91ff54962e4e1d5716c339718ebea926db6e24aacf35ebc362aafcfde4d6264d56a6edf430ae4ade75cdf9c121c3708211407d5c7ad23e2bc8dfe0d71e588b01d2ada797830315ba616f6c79030481dde1d8aa1a37676aaa9a48aec1dcc535daf2547cff2d43c58acd7e09473c46cada1112e82b0502d057ce6a8a629836ea293be93c5d228169c46d0643378e20429ba09e0a236f8dc56a23e9d38509a72e3bb115dc7c959af913b7d561d17fd3df8d490e2d91c4ae16429a3ecfa45212dbd6ccd80d4ac5956ade21c46b4960c08570e0af69ee39d1c23b194f20bc4d5d5cb5ec0b1e3a376a51700d166dce2b09a6e0c2285af7d36c4d0178a1acad0bfd8d913c44506987df6406ad7f134927c5d46be261cc4025f6310e8cb8284f03ae75a75d4ca43ba1d578b1be69d76503370f95eb98a769eaf1e7c3032907f9ab50c450b7177a804b2e9cb8c5a6fe5bbeb07d0ad176961f817119989f090ad162cae302242651c0c69e7b52c36665be795538afc3aef77a1cd0a36a170d572f56eb79e07ee6544da446798b5e0a0ed92cd205288824335b0444e5eda4347d28be9b2a128d50f983c5b16ea2792eda5d352b609a08a15268e758e024dbde13ae42008c03c608c6bb1971c9eb7a1842129b056b9c0690a88c1aea43f9bc20d8e132575a1eae77ba2fe24ee780f42e6b73b7022049518f1c231fd4fe3e91ca443980e52507ebc97f8fc49036c6e141c0e74603ac02814aa0928381228f7aadd798dbaac3603099e94224dd0b51466d392d276f19b990e8b351b73d3e284fc24f1c1ee0bffad64d9415ae8ab358b01dbc7eedfadb181ab8080d0f9c151c445419ee670e8376a112a631c5ec4092aca077086299e406ab304c7f864da801147b0e09b9f8873c85e952550b62684ca9217d1c0763930b019871787b07cabe0ef8b541c2375bc7ceb4ec1153e6f8a48371f7f80c69dcd37a7d53053fde41f87231abdbed68f195ddc6082b9d0e55207fea2c4d0c8045d44bfe55de2fb71ed75f12c0105a2ea480678b73100a943c45b6d2d5556ece82f02bd12d8785f38ac96bba1167b27b40dbd4d1677cf0b96f9311382cc110f739ffba634fa5163c9e1bd6f0279356aeab301eca2398525ec136d9d3aab4634a1fe14b365c8ad4f98e217cdba327945dfe69f3a3b7f8cd932bd761b2b264a371b104559d0a69c8e7ac053512f3e7fd2cf64278f33f7288958042b3166ff0f05e174378c80ab8d01332e862f17e8cd5c74b3de9acb51e9526d8d3305fa51b447fcb289b26c96ff06e6d50a639514491077c9f70757b74c7e26800688a05274fdcf2697e69455742126dc0df95512e478417db81440a5d2f8c671df00d0bafca53a8ed4bf6c8ea0a4c8af39c7bb1103b828547b669b75f780d9d78ddd811dd1a639576b19805088c33e6e7855cc360827516f4de8f4788bf81feb45d6d31ba5277bca2c86d84f8a"
		htlcTxHex      = "020000000001042d54f0cbba265e7f2a0873ce9e03879b706b361d3ee18e8d4a29300c0948bee50000000017160014a411cc351bd7c5572a6ba5ba16e3f3f92106425bfeffffff330565534d05b1d0d4b8a1878f1a5c41d3f571f793c528a44d72af276a259bbd000000001716001443891b3727c96d1e1e91aec69b167123a429ed32feffffff4bb7cba13999b98510769270d161f2a8e0f80f1033c72579a78c105ab2b30ebf00000000171600141c9e61f3951956b00c6f215e75e7fb9a5d93988ffefffffffaa153f428dc6c0aa0be74fa56d6563556b4286d5f9b8a431d5acf0b08f00240000000001716001488ef3cc57c509e843387a7c74e55fdc27fe962f2feffffff0178b4000000000000220020b476030d986b5a4bb084702eec3d895a40d02cfef216f09962cac94a8bd119a102473044022059247039c8e3e95d2eb289e35f81b67e5811d5a757b2117cc9dab1cebf830b2e02204d15406ea075d3d4973344b9bf7c151bb39a7e2876551b8500057dce4591fb870121033d8377beee8caf5fccd958bfacfdd3b5dc1948dfad9fb09768d11e69abb76f3402473044022042e242e0c3adca4f8b3a19cbd8eefd80bf8a43debbe51c5759b2f607b7332bc6022020be1288b8dcddfdc1569c766490e8c0ea668ea1bc122d40b59cbe6589e6938701210243b39e7a2d42937b5aecf14d27b200f7486397d92cbb166ae9f3c11259caf9c602473044022024b16f6042b9a664a46ab0c8cfc3424f8a100309f4cb51e9969a62a257255e2b022053f09e518d2ba73f74d7f3feca98df2b38eb45870325a433457277caf1ce7e6a012103f0386dfb783fc1b55d50aa089326dc008fe2fa963b44d3851bc307bbc419d1c4024730440220346c877b9e983a20fbc52fca2132717e2647bba5489e4127e8032c263252c7d502202cea58c807f60c4b6e10262a2eb59daa8bf637465a4ad67db83c59768088bfb70121020dd5d5044667ebd71f0917a6182435afeb408b4142932c9f5d852b5623ca5d6800000000"

		preimageHex = "D7EA6B6FE58119AA061CBA3A3C1B556DE966053EE0B8A455A2FA5BA6EAE978FA"

		encodedMuunKey = "tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8"
		encodedUserKey = "tprv8deMke4d4jbc5wVYMaDpoqsXYuEPvwLPN43iRRwdZqVJCr9Wc9xh5194mMJeLTkLfQHS5CgkuXbZ9uwK9Eogcx2t7JoscYtrFirGsc3kgCr"

		muunSigHex = "3045022100c4bef5d32c5ed3530cd258df645dfb0298744dee7820095aca1a188a3b2138c102201669e21db8ee4d2b090cbb18e3e52bce40fc5e07be73c1be4d26c9f13c02e69701"
	)

	txOut, _ := hex.DecodeString(hexTxOut)

	paymentHash, _ := hex.DecodeString(paymentHashHex)
	sphinx, _ := hex.DecodeString(sphinxHex)
	htlcTx, _ := hex.DecodeString(htlcTxHex)

	preimage, _ := hex.DecodeString(preimageHex)

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, "m/schema:1'/recovery:1'", Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, "m/schema:1'/recovery:1'", Regtest())

	muunSig, _ := hex.DecodeString(muunSigHex)

	inputs := []Input{
		&input{
			outpoint:      outpoint{index: txIndex, amount: txAmount, txId: txOut},
			address:       addresses.New(addresses.IncomingSwap, txAddressPath, txAddress),
			muunSignature: muunSig,
			incomingSwap: inputIncomingSwap{
				sphinx:              sphinx,
				htlcTx:              htlcTx,
				paymentHash:         paymentHash,
				swapServerPublicKey: "03912b4cfbd725133cbc319b444c1dad96a8bb0fcf840adc28c8e05e84ecbaa89b",
				expirationHeight:    5528,
				collectInSats:       0,
				preimage:            preimage,
				htlcOutputKeyPath:   "m/schema:1'/recovery:1'/invoices:4/1189547938/512484821/1",
				// Instead of taking these last 2 values from the local/client's DB we take them from Houston's data
			},
		},
	}

	inputList := &InputList{inputs: inputs}
	rawTx, _ := hex.DecodeString(hexTx)

	setup()

	nonces := GenerateMusigNonces(len(inputList.inputs))
	partial, _ := NewPartiallySignedTransaction(inputList, rawTx, nonces)

	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.Deserialize(bytes.NewReader(signedRawTx.Bytes))

	verifyInput(t, signedTx, htlcTxHex, txIndex, 0)
}

func TestPartiallySignedTransaction_Verify(t *testing.T) {

	const (
		hexTx1 = "0100000002a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffffdf39591fa749826f87a3d7e5fd5f0468d338c3d81dd3b2c953534b0210f98c560000000000ffffffff02a8d6c20400000000220020452f4ae303ec79acd2bce8f7ddb6469f1060d9146003ea34887e5bbdf021c787000e2707000000002200202ccf0ca2c9b5077ce8345785af26a39277003886fb358877e4083a3fcc5cd66700000000"

		txIndex1          = 1
		txAmount1         = 100000000
		txIdHex1          = "ae57aa2bc55298cc79459db058a91a45bc4a5cd57c9a988ce4de31b64ac01ca5"
		txAddressPath1    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress1        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion1 = addresses.V4

		txIndex2          = 0
		txAmount2         = 100000000
		txIdHex2          = "568cf910024b5353c9b2d31dd8c338d368045ffde5d7a3876f8249a71f5939df"
		txAddressPath2    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress2        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion2 = addresses.V4

		changeAddress1 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath1    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion1 = addresses.V4

		hexTx2 = "01000000010ead2fa0d6866d0414aba97fd8f1b242fdc3d4c8e7771e40969402319b6e876b0000000000ffffffff02922988040000000017a914d1ac5d61107d2bef187d1aef5cfd3536f4fd5dbe87d6b2050100000000220020bac6de765432ee16e10ce268341062f8f5a417b15a7f6ee8fe903e6d7470f0f700000000"

		txIndex3          = 0
		txAmount3         = 93266680
		txIdHex3          = "6b876e9b31029496401e77e7c8d4c3fd42b2f1d87fa9ab14046d86d6a02fad0e"
		txAddressPath3    = "m/schema:1'/recovery:1'/change:0/8"
		txAddress3        = "bcrt1q9yzsghvmmn7wv3esylrvn3c469s4ce4thk7qmxdly4tzk4f8vvjsqv0crh"
		txAddressVersion3 = addresses.V4

		hexTx4 = "0100000002a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffffdf39591fa749826f87a3d7e5fd5f0468d338c3d81dd3b2c953534b0210f98c560000000000ffffffff01000e2707000000002200202ccf0ca2c9b5077ce8345785af26a39277003886fb358877e4083a3fcc5cd66700000000"

		changeAddress2 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath2    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion2 = addresses.V4

		hexTx5 = "0100000001a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffff02a086010000000000220020452f4ae303ec79acd2bce8f7ddb6469f1060d9146003ea34887e5bbdf021c787302dfa02000000002200202ccf0ca2c9b5077ce8345785af26a39277003886fb358877e4083a3fcc5cd66700000000"

		txIndex5          = 1
		txAmount5         = 100000000
		txIdHex5          = "907e3c0c82b36b11b8543c9e058fe6e23d5ad35881f776e1ca9049e622f2cf80"
		txAddressPath5    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress5        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion5 = addresses.V4

		changeAddress5 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath5    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion5 = addresses.V4

		hexTx6 = "0100000001a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffff02a086010000000000220020452f4ae303ec79acd2bce8f7ddb6469f1060d9146003ea34887e5bbdf021c787e8030000000000002200202ccf0ca2c9b5077ce8345785af26a39277003886fb358877e4083a3fcc5cd66700000000"

		txIndex6          = 1
		txAmount6         = 100000000
		txIdHex6          = "69391b987ec374f8e61a5fabb94899cd5efe802ee0f4d890bbbdbd18b05cac0f"
		txAddressPath6    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress6        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion6 = addresses.V4

		changeAddress6 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath6    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion6 = addresses.V4

		hexTx7 = "0100000001a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffff01a086010000000000220020452f4ae303ec79acd2bce8f7ddb6469f1060d9146003ea34887e5bbdf021c78700000000"

		txIndex7          = 1
		txAmount7         = 100000000
		txIdHex7          = "383136ae84ddd35059a087fb56571f4809d97f09c04eac779ba31f6121818461"
		txAddressPath7    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress7        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion7 = addresses.V4

		changeAddress7 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath7    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion7 = addresses.V4

		encodedUserKey = "tpubDAKxNPypXDF3GNCpXFUh6sCdxz7DY9eKMgFxYBgyRSiYWXrBLgdtkPuMbQQzrsYLVyPPSHmNcduLRRd9TSMaYrGLryp8KNkkYBm6eka1Bem"
		encodedMuunKey = "tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8"

		basePath = "m/schema:1'/recovery:1'"
	)

	txId1, _ := hex.DecodeString(txIdHex1)
	txId2, _ := hex.DecodeString(txIdHex2)
	txId3, _ := hex.DecodeString(txIdHex3)
	txId5, _ := hex.DecodeString(txIdHex5)
	txId6, _ := hex.DecodeString(txIdHex6)
	txId7, _ := hex.DecodeString(txIdHex7)

	userPublicKey, _ := NewHDPublicKeyFromString(
		encodedUserKey,
		basePath,
		Regtest())

	muunPublicKey, _ := NewHDPublicKeyFromString(
		encodedMuunKey,
		basePath,
		Regtest())

	tx := wire.NewMsgTx(1)
	txBytes, _ := hex.DecodeString(hexTx1)
	_ = tx.Deserialize(bytes.NewReader(txBytes))
	// Only set one input to reduce boilerplate
	tx.TxIn = []*wire.TxIn{tx.TxIn[0]}
	// have 100000000 as input
	// say 100_000 as change
	// destination
	tx.TxOut[1].Value = 1000
	// change
	tx.TxOut[0].Value = 100_000
	tx.TxOut = []*wire.TxOut{tx.TxOut[0]}
	buffer := new(bytes.Buffer)
	_ = tx.Serialize(buffer)

	println(tx.TxHash().String())
	println(hex.EncodeToString(buffer.Bytes()))

	type fields struct {
		tx     string
		inputs []Input
	}
	type args struct {
		expectations   *SigningExpectations
		userPublicKey  *HDPublicKey
		muunPublickKey *HDPublicKey
	}
	firstInput := input{
		outpoint: outpoint{index: txIndex1, amount: txAmount1, txId: txId1},
		address:  addresses.New(txAddressVersion1, txAddressPath1, txAddress1),
	}
	secondInput := input{
		outpoint: outpoint{index: txIndex2, amount: txAmount2, txId: txId2},
		address:  addresses.New(txAddressVersion2, txAddressPath2, txAddress2),
	}
	secondInputGeneratingDust := input{
		outpoint: outpoint{index: txIndex2, amount: 120000000 - txAmount1 + 122200 + 100 /* dust */, txId: txId2},
		address:  addresses.New(txAddressVersion2, txAddressPath2, txAddress2),
	}
	thirdInput := input{
		outpoint: outpoint{index: txIndex3, amount: txAmount3, txId: txId3},
		address:  addresses.New(txAddressVersion3, txAddressPath3, txAddress3),
	}
	inputForFifthTx := input{
		outpoint: outpoint{index: txIndex5, amount: txAmount5, txId: txId5},
		address:  addresses.New(txAddressVersion5, txAddressPath5, txAddress5),
	}
	inputForSixthTx := input{
		outpoint: outpoint{index: txIndex6, amount: txAmount6, txId: txId6},
		address:  addresses.New(txAddressVersion6, txAddressPath6, txAddress6),
	}
	inputForSeventhTx := input{
		outpoint: outpoint{index: txIndex7, amount: txAmount7, txId: txId7},
		address:  addresses.New(txAddressVersion7, txAddressPath7, txAddress7),
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr bool
	}{
		{
			name: "2 inputs, one change",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      addresses.New(changeVersion1, changePath1, changeAddress1),
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
		},
		{
			name: "lied about destination amount",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      110000000,
					change:      addresses.New(changeVersion1, changePath1, changeAddress1),
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "lied about change",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      addresses.New(changeVersion1, basePath+"/123", changeAddress1),
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "lied about destination",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "2N2giv9tsN3pV7Rkm89SReRBgdqKNBESVBk",
					amount:      120000000,
					change:      addresses.New(changeVersion1, changePath1, changeAddress1),
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "lied about fee",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      addresses.New(changeVersion1, changePath1, changeAddress1),
					fee:         12200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "wasnt expecting change",
			fields: fields{
				tx:     hexTx1,
				inputs: []Input{&firstInput, &secondInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      nil,
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "lying change",
			fields: fields{
				tx:     hexTx2,
				inputs: []Input{&thirdInput},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1qhtrduaj5xthpdcgvuf5rgyrzlr66g9a3tflka687jqlx6ars7rms0flpmy",
					amount:      17150678,
					change:      addresses.New(changeVersion2, changePath2, changeAddress2),
					fee:         83600,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "inputs generating dust",
			fields: fields{
				tx:     hexTx4,
				inputs: []Input{&firstInput, &secondInputGeneratingDust},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      nil,
					fee:         122200,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: false,
		},
		{
			name: "alternative with half the destination amount with change",
			fields: fields{
				tx:     hexTx5,
				inputs: []Input{&inputForFifthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount5 - 100_000 - 100,
					change:      addresses.New(changeVersion5, changePath5, changeAddress5),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
		},
		{
			name: "alternative with more change than expected",
			fields: fields{
				tx:     hexTx5,
				inputs: []Input{&inputForFifthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount5 - 10_000 - 100,
					change:      addresses.New(changeVersion5, changePath5, changeAddress5),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "alternative with a higher amount than expected",
			fields: fields{
				tx:     hexTx5,
				inputs: []Input{&inputForFifthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      1000,
					change:      addresses.New(changeVersion5, changePath5, changeAddress5),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "alternative with mismatched destination address",
			fields: fields{
				tx:     hexTx5,
				inputs: []Input{&inputForFifthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9yzsghvmmn7wv3esylrvn3c469s4ce4thk7qmxdly4tzk4f8vvjsqv0crh",
					amount:      txAmount5 - 100_000 - 100,
					change:      addresses.New(changeVersion5, changePath5, changeAddress5),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "alternative with mismatched change address",
			fields: fields{
				tx:     hexTx5,
				inputs: []Input{&inputForFifthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount5 - 100_000 - 100,
					change:      addresses.New(changeVersion5, changePath5, txAddress1),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
		{
			name: "alternative with near-dust destination amount with change",
			fields: fields{
				tx:     hexTx6,
				inputs: []Input{&inputForSixthTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount6 - 100_000 - 100,
					change:      addresses.New(changeVersion6, changePath6, changeAddress6),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
		},
		{
			name: "alternative with no destination change",
			fields: fields{
				tx:     hexTx7,
				inputs: []Input{&inputForSeventhTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount7 - 100_000 - 100,
					change:      addresses.New(changeVersion7, changePath7, changeAddress7),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
		},
		{
			name: "alternative with no destination and more change",
			fields: fields{
				tx:     hexTx7,
				inputs: []Input{&inputForSeventhTx},
			},
			args: args{
				expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      txAmount7 - 9_000 - 100,
					change:      addresses.New(changeVersion7, changePath7, changeAddress7),
					fee:         100,
					alternative: true,
				},
				userPublicKey:  userPublicKey,
				muunPublickKey: muunPublicKey,
			},
			wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			inputList := &InputList{inputs: tt.fields.inputs}
			rawTx, _ := hex.DecodeString(tt.fields.tx)
			nonces := GenerateMusigNonces(len(inputList.inputs))
			p, err := NewPartiallySignedTransaction(inputList, rawTx, nonces)
			if err != nil {
				panic(err)
			}

			// We do something a bit particular here. We always run alternative and
			// non-alternative verifications. This allows us to test the invariant
			// that valid alternative are not valid non-alternative and vice-versa.

			nonAlternativeExpectations := *tt.args.expectations
			nonAlternativeExpectations.alternative = false

			alternativeExpectations := tt.args.expectations.ForAlternativeTransaction()

			errNonAlternative := p.Verify(&nonAlternativeExpectations, tt.args.userPublicKey, tt.args.muunPublickKey)
			errAlternative := p.Verify(alternativeExpectations, tt.args.userPublicKey, tt.args.muunPublickKey)

			t.Logf("test %v non-alternative returned %v", tt.name, errNonAlternative)
			t.Logf("test %v alternative returned %v", tt.name, errAlternative)

			if tt.args.expectations.alternative {

				if (errAlternative != nil) != tt.wantErr {
					t.Errorf("Verify() error = %v, wantErr %v", errAlternative, tt.wantErr)
				}

				if errNonAlternative == nil && errAlternative == nil {
					t.Errorf("Verify() alternative TX should not verify as non-alternative")
				}

			} else {

				if (errNonAlternative != nil) != tt.wantErr {
					t.Errorf("Verify() error = %v, wantErr %v", errNonAlternative, tt.wantErr)
				}

				if errNonAlternative == nil && errAlternative == nil {
					t.Errorf("Verify() non-alternative TX should not verify as alternative")
				}
			}

		})
	}
}
