package libwallet

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
)

const (
	basePath = "m/schema:1'/recovery:1'"
)

type input struct {
	outpoint        outpoint
	address         muunAddress
	userSignature   []byte
	muunSignature   []byte
	submarineSwapV1 inputSubmarineSwapV1
	submarineSwapV2 inputSubmarineSwapV2
}

func (i *input) OutPoint() Outpoint {
	return &i.outpoint
}

func (i *input) Address() MuunAddress {
	return &i.address
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
			address:  muunAddress{address: originAddress, derivationPath: addressPath, version: addressV1},
		},
	}

	partial, _ := NewPartiallySignedTransaction(hexTx)
	partial.inputs = inputs

	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	// We dont need to use the muunKey in V1
	signedRawTx, err := partial.Sign(userKey, userKey.PublicKey())

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.BtcDecode(bytes.NewBuffer(signedRawTx.Bytes), 0, wire.WitnessEncoding)

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
			address:       muunAddress{address: originAddress, derivationPath: addressPath, version: 2},
			muunSignature: muunSig1},
		&input{
			outpoint:      outpoint{index: txIndex2, amount: txAmount2, txId: txOut2},
			address:       muunAddress{address: originAddress, derivationPath: addressPath, version: 2},
			muunSignature: muunSig2},
		&input{
			outpoint:      outpoint{index: txIndex3, amount: txAmount3, txId: txOut3},
			address:       muunAddress{address: originAddress, derivationPath: addressPath, version: 2},
			muunSignature: muunSig3},
		&input{
			outpoint:      outpoint{index: txIndex4, amount: txAmount4, txId: txOut4},
			address:       muunAddress{address: originAddress, derivationPath: addressPath, version: 2},
			muunSignature: muunSig4},
	}

	partial, _ := NewPartiallySignedTransaction(hexTx)
	partial.inputs = inputs

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.BtcDecode(bytes.NewBuffer(signedRawTx.Bytes), 0, wire.WitnessEncoding)

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
			address:       muunAddress{address: originAddress, derivationPath: addressPath, version: addressV3},
			muunSignature: muunSig1},
	}

	partial, _ := NewPartiallySignedTransaction(hexTx)
	partial.inputs = inputs

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.BtcDecode(bytes.NewBuffer(signedRawTx.Bytes), 0, wire.WitnessEncoding)

	verifyInput(t, signedTx, hexTx1, txIndex1, 0)
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
			address:       muunAddress{address: txAddress1, derivationPath: txAddressPath1, version: addressV3},
			muunSignature: muunSig1,
		},
		&input{
			outpoint: outpoint{index: txIndex2, amount: txAmount2, txId: txOut2},
			address:  muunAddress{address: txAddress2, derivationPath: txAddressPath2, version: addressSubmarineSwapV1},
			submarineSwapV1: inputSubmarineSwapV1{
				refundAddress:   txRefundAddress2,
				paymentHash256:  paymentHash2,
				serverPublicKey: serverPubKey2,
				lockTime:        txLockTime2,
			},
		},
	}

	partial, _ := NewPartiallySignedTransaction(hexTx)
	partial.inputs = inputs

	muunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	userKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.BtcDecode(bytes.NewBuffer(signedRawTx.Bytes), 0, wire.WitnessEncoding)

	verifyInput(t, signedTx, hexTx1, txIndex1, 0)
	verifyInput(t, signedTx, hexTx2, txIndex2, 1)
}

func verifyInput(t *testing.T, signedTx *wire.MsgTx, hexPrevTx string, prevIndex, index int) {

	// Uncomment the next block if you need to see what the script engine outputs
	txscript.DisableLog()
	// logger := btclog.NewBackend(os.Stderr).Logger("test")
	// logger.SetLevel(btclog.LevelTrace)
	// txscript.UseLogger(logger)

	prevTx := wire.NewMsgTx(0)

	rawPrevTx, _ := hex.DecodeString(hexPrevTx)
	prevTx.BtcDecode(bytes.NewBuffer(rawPrevTx), 0, wire.WitnessEncoding)

	flags := txscript.ScriptBip16 | txscript.ScriptVerifyDERSignatures |
		txscript.ScriptStrictMultiSig | txscript.ScriptDiscourageUpgradableNops |
		txscript.ScriptVerifyStrictEncoding | txscript.ScriptVerifyLowS |
		txscript.ScriptVerifyWitness | txscript.ScriptVerifyCheckLockTimeVerify

	vm, err := txscript.NewEngine(prevTx.TxOut[prevIndex].PkScript, signedTx, index, flags, nil, nil, prevTx.TxOut[prevIndex].Value)
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
			address:  muunAddress{address: txAddress2, derivationPath: txAddressPath2, version: addressSubmarineSwapV2},
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

	partial, _ := NewPartiallySignedTransaction(hexTx)
	partial.inputs = inputs

	signedRawTx, err := partial.Sign(userKey, muunKey)

	if err != nil {
		t.Fatalf("failed to sign tx due to %v", err)
	}

	signedTx := wire.NewMsgTx(0)
	signedTx.BtcDecode(bytes.NewBuffer(signedRawTx.Bytes), 0, wire.WitnessEncoding)

	verifyInput(t, signedTx, hexTx2, txIndex2, 0)
}

func TestPartiallySignedTransaction_Verify(t *testing.T) {

	const (
		hexTx1 = "0100000002a51cc04ab631dee48c989a7cd55c4abc451aa958b09d4579cc9852c52baa57ae0100000000ffffffffdf39591fa749826f87a3d7e5fd5f0468d338c3d81dd3b2c953534b0210f98c560000000000ffffffff02a8d6c20400000000220020452f4ae303ec79acd2bce8f7ddb6469f1060d9146003ea34887e5bbdf021c787000e2707000000002200202ccf0ca2c9b5077ce8345785af26a39277003886fb358877e4083a3fcc5cd66700000000"

		txIndex1          = 1
		txAmount1         = 100000000
		txIdHex1          = "ae57aa2bc55298cc79459db058a91a45bc4a5cd57c9a988ce4de31b64ac01ca5"
		txAddressPath1    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress1        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion1 = addressV4

		txIndex2          = 0
		txAmount2         = 100000000
		txIdHex2          = "568cf910024b5353c9b2d31dd8c338d368045ffde5d7a3876f8249a71f5939df"
		txAddressPath2    = "m/schema:1'/recovery:1'/external:1/0"
		txAddress2        = "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye"
		txAddressVersion2 = addressV4

		changeAddress1 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath1    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion1 = addressV4

		hexTx2 = "01000000010ead2fa0d6866d0414aba97fd8f1b242fdc3d4c8e7771e40969402319b6e876b0000000000ffffffff02922988040000000017a914d1ac5d61107d2bef187d1aef5cfd3536f4fd5dbe87d6b2050100000000220020bac6de765432ee16e10ce268341062f8f5a417b15a7f6ee8fe903e6d7470f0f700000000"

		txIndex3          = 0
		txAmount3         = 93266680
		txIdHex3          = "6b876e9b31029496401e77e7c8d4c3fd42b2f1d87fa9ab14046d86d6a02fad0e"
		txAddressPath3    = "m/schema:1'/recovery:1'/change:0/8"
		txAddress3        = "bcrt1q9yzsghvmmn7wv3esylrvn3c469s4ce4thk7qmxdly4tzk4f8vvjsqv0crh"
		txAddressVersion3 = addressV4

		changeAddress2 = "bcrt1qg5h54ccra3u6e54uarmamdjxnugxpkg5vqp75dyg0edmmuppc7rsdfcvcp"
		changePath2    = "m/schema:1'/recovery:1'/change:0/1"
		changeVersion2 = addressV4

		encodedUserKey = "tpubDAKxNPypXDF3GNCpXFUh6sCdxz7DY9eKMgFxYBgyRSiYWXrBLgdtkPuMbQQzrsYLVyPPSHmNcduLRRd9TSMaYrGLryp8KNkkYBm6eka1Bem"
		encodedMuunKey = "tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8"

		basePath = "m/schema:1'/recovery:1'"
	)

	txId1, _ := hex.DecodeString(txIdHex1)
	txId2, _ := hex.DecodeString(txIdHex2)
	txId3, _ := hex.DecodeString(txIdHex3)

	userPublicKey, _ := NewHDPublicKeyFromString(
		encodedUserKey,
		basePath,
		Regtest())

	muunPublicKey, _ := NewHDPublicKeyFromString(
		encodedMuunKey,
		basePath,
		Regtest())

	type fields struct {
		tx           *wire.MsgTx
		inputs       []Input
		Expectations *SigningExpectations
	}
	type args struct {
		userPublicKey  *HDPublicKey
		muunPublickKey *HDPublicKey
	}
	firstInput := input{
		outpoint: outpoint{index: txIndex1, amount: txAmount1, txId: txId1},
		address:  muunAddress{address: txAddress1, derivationPath: txAddressPath1, version: txAddressVersion1},
	}
	secondInput := input{
		outpoint: outpoint{index: txIndex2, amount: txAmount2, txId: txId2},
		address:  muunAddress{address: txAddress2, derivationPath: txAddressPath2, version: txAddressVersion2},
	}
	thirdInput := input{
		outpoint: outpoint{index: txIndex3, amount: txAmount3, txId: txId3},
		address:  muunAddress{address: txAddress3, derivationPath: txAddressPath3, version: txAddressVersion3},
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
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      &muunAddress{address: changeAddress1, derivationPath: changePath1, version: changeVersion1},
					fee:         122200,
				},
			},
			args: args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
		},
		{
			name: "lied about destination amount",
			fields: fields{
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      110000000,
					change:      &muunAddress{address: changeAddress1, derivationPath: changePath1, version: changeVersion1},
					fee:         122200,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
		{
			name: "lied about change",
			fields: fields{
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      &muunAddress{address: changeAddress1, derivationPath: basePath + "/123", version: changeVersion1},
					fee:         122200,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
		{
			name: "lied about destination",
			fields: fields{
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "2N2giv9tsN3pV7Rkm89SReRBgdqKNBESVBk",
					amount:      120000000,
					change:      &muunAddress{address: changeAddress1, derivationPath: changePath1, version: changeVersion1},
					fee:         122200,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
		{
			name: "lied about fee",
			fields: fields{
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      &muunAddress{address: changeAddress1, derivationPath: changePath1, version: changeVersion1},
					fee:         12200,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
		{
			name: "wasnt expecting change",
			fields: fields{
				tx:     parseTx(hexTx1),
				inputs: []Input{&firstInput, &secondInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1q9n8segkfk5rhe6p527z67f4rjfmsqwyxlv6csalypqarlnzu6ens8cm8ye",
					amount:      120000000,
					change:      nil,
					fee:         122200,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
		{
			name: "lying change",
			fields: fields{
				tx:     parseTx(hexTx2),
				inputs: []Input{&thirdInput},
				Expectations: &SigningExpectations{
					destination: "bcrt1qhtrduaj5xthpdcgvuf5rgyrzlr66g9a3tflka687jqlx6ars7rms0flpmy",
					amount:      17150678,
					change:      &muunAddress{address: changeAddress2, derivationPath: changePath2, version: changeVersion2},
					fee:         83600,
				},
			},
			args:    args{userPublicKey: userPublicKey, muunPublickKey: muunPublicKey},
			wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			p := &PartiallySignedTransaction{
				tx:           tt.fields.tx,
				inputs:       tt.fields.inputs,
				Expectations: tt.fields.Expectations,
			}
			err := p.Verify(tt.args.userPublicKey, tt.args.muunPublickKey)
			t.Logf("test %v returned %v", tt.name, err)
			if (err != nil) != tt.wantErr {
				t.Errorf("Verify() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func parseTx(hexTx string) *wire.MsgTx {

	rawTx, _ := hex.DecodeString(hexTx)

	tx := wire.NewMsgTx(0)
	tx.BtcDecode(bytes.NewBuffer(rawTx), 0, wire.WitnessEncoding)

	return tx
}
