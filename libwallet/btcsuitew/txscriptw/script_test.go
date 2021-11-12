package txscriptw

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"testing"
	_ "unsafe"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
)

// These test cases were taken from rust-bitcoin, which in turn took them from Bitcoin Core:
var sigHashTestCases = []sigHashTestCase{
	{
		tx:       "020000000164eb050a5e3da0c2a65e4786f26d753b7bc69691fabccafb11f7acef36641f1846010000003101b2b404392a22000000000017a9147f2bde86fe78bf68a0544a4f290e12f0b7e0a08c87580200000000000017a91425d11723074ecfb96a0a83c3956bfaf362ae0c908758020000000000001600147e20f938993641de67bb0cdd71682aa34c4d29ad5802000000000000160014c64984dc8761acfa99418bd6bedc79b9287d652d72000000",
		prevOuts: "01365724000000000023542156b39dab4f8f3508e0432cfb41fab110170acaa2d4c42539cb90a4dc7c093bc500",
		index:    0,
		hashType: txscript.SigHashOld,
		// expectSigHash: "33ca0ebfb4a945eeee9569fc0f5040221275f88690b7f8592ada88ce3bdf6703",
		expectError: true,
	},
	{
		tx:            "0200000002fff49be59befe7566050737910f6ccdc5e749c7f8860ddc140386463d88c5ad0f3000000002cf68eb4a3d67f9d4c079249f7e4f27b8854815cb1ed13842d4fbf395f9e217fd605ee24090100000065235d9203f458520000000000160014b6d48333bb13b4c644e57c43a9a26df3a44b785e58020000000000001976a914eea9461a9e1e3f765d3af3e726162e0229fe3eb688ac58020000000000001976a9143a8869c9f2b5ea1d4ff3aeeb6a8fb2fffb1ad5fe88ac0ad7125c",
		prevOuts:      "02591f220000000000225120f25ad35583ea31998d968871d7de1abd2a52f6fe4178b54ea158274806ff4ece48fb310000000000225120f25ad35583ea31998d968871d7de1abd2a52f6fe4178b54ea158274806ff4ece",
		index:         1,
		hashType:      txscript.SigHashAll,
		expectSigHash: "626ab955d58c9a8a600a0c580549d06dc7da4e802eb2a531f62a588e430967a8",
		expectError:   false,
	},
	{
		tx:       "0200000001350005f65aa830ced2079df348e2d8c2bdb4f10e2dde6a161d8a07b40d1ad87dae000000001611d0d603d9dc0e000000000017a914459b6d7d6bbb4d8837b4bf7e9a4556f952da2f5c8758020000000000001976a9141dd70e1299ffc2d5b51f6f87de9dfe9398c33cbb88ac58020000000000001976a9141dd70e1299ffc2d5b51f6f87de9dfe9398c33cbb88aca71c1f4f",
		prevOuts: "01c4811000000000002251201bf9297d0a2968ae6693aadd0fa514717afefd218087a239afb7418e2d22e65c",
		index:    0,
		hashType: txscript.SigHashAll | txscript.SigHashAnyOneCanPay,
		// expectSigHash: "dfa9437f9c9a1d1f9af271f79f2f5482f287cdb0d2e03fa92c8a9b216cc6061c",
		expectError: true,
	},
	{
		tx:       "020000000185bed1a6da2bffbd60ec681a1bfb71c5111d6395b99b3f8b2bf90167111bcb18f5010000007c83ace802ded24a00000000001600142c4698f9f7a773866879755aa78c516fb332af8e5802000000000000160014d38639dfbac4259323b98a472405db0c461b31fa61073747",
		prevOuts: "0144c84d0000000000225120e3f2107989c88e67296ab2faca930efa2e3a5bd3ff0904835a11c9e807458621",
		index:    0,
		hashType: txscript.SigHashNone,
		// expectSigHash: "3129de36a5d05fff97ffca31eb75fcccbbbc27b3147a7a36a9e4b45d8b625067",
		expectError: true,
	},
	{
		tx:       "eb93dbb901028c8515589dac980b6e7f8e4088b77ed866ca0d6d210a7218b6fd0f6b22dd6d7300000000eb4740a9047efc0e0000000000160014913da2128d8fcf292b3691db0e187414aa1783825802000000000000160014913da2128d8fcf292b3691db0e187414aa178382580200000000000017a9143dd27f01c6f7ef9bb9159937b17f17065ed01a0c875802000000000000160014d7630e19df70ada9905ede1722b800c0005f246641000000",
		prevOuts: "013fed110000000000225120eb536ae8c33580290630fc495046e998086a64f8f33b93b07967d9029b265c55",
		index:    0,
		hashType: txscript.SigHashNone | txscript.SigHashAnyOneCanPay,
		// expectSigHash: "2441e8b0e063a2083ee790f14f2045022f07258ddde5ee01de543c9e789d80ae",
		expectError: true,
	},
	{
		tx:       "02000000017836b409a5fed32211407e44b971591f2032053f14701fb5b3a30c0ff382f2cc9c0100000061ac55f60288fb5600000000001976a9144ea02f6f182b082fb6ce47e36bbde390b6a41b5088ac58020000000000001976a9144ea02f6f182b082fb6ce47e36bbde390b6a41b5088ace4000000",
		prevOuts: "01efa558000000000022512007071ea3dc7e331b0687d0193d1e6d6ed10e645ef36f10ef8831d5e522ac9e80",
		index:    0,
		hashType: txscript.SigHashSingle,
		// expectSigHash: "30239345177cadd0e3ea413d49803580abb6cb27971b481b7788a78d35117a88",
		expectError: true,
	},
	{
		tx:       "0100000001aa6deae89d5e0aaca58714fc76ef6f3c8284224888089232d4e663843ed3ab3eae010000008b6657a60450cb4c0000000000160014a3d42b5413ef0c0701c4702f3cd7d4df222c147058020000000000001976a91430b4ed8723a4ee8992aa2c8814cfe5c3ad0ab9d988ac5802000000000000160014365b1166a6ed0a5e8e9dff17a6d00bbb43454bc758020000000000001976a914bc98c51a84fe7fad5dc380eb8b39586eff47241688ac4f313247",
		prevOuts: "0107af4e00000000002251202c36d243dfc06cb56a248e62df27ecba7417307511a81ae61aa41c597a929c69",
		index:    0,
		hashType: txscript.SigHashSingle | txscript.SigHashAnyOneCanPay,
		// expectSigHash: "bf9c83f26c6dd16449e4921f813f551c4218e86f2ec906ca8611175b41b566df",
		expectError: true,
	},
}

func TestTaprootSigHash(t *testing.T) {
	for i, testCase := range sigHashTestCases {
		tx := testCase.ParseTx()
		prevOuts := testCase.ParsePrevOuts()

		sigHashes := NewTaprootSigHashes(tx, prevOuts)

		sigHash, err := CalcTaprootSigHash(tx, sigHashes, testCase.index, testCase.hashType)
		if (err != nil) != testCase.expectError {
			t.Fatalf("case %d: expect error %v, actual error: %v", i, testCase.expectError, err)
		}

		if !bytes.Equal(sigHash, testCase.ParseExpectedSigHash()) {
			t.Fatalf("case %d: sigHash does not match expected value", i)
		}
	}
}

type sigHashTestCase struct {
	tx            string
	prevOuts      string
	index         int
	hashType      txscript.SigHashType
	expectSigHash string
	expectError   bool
}

func (c *sigHashTestCase) ParseTx() *wire.MsgTx {
	b, _ := hex.DecodeString(c.tx)
	r := bytes.NewReader(b)

	tx := wire.NewMsgTx(0)
	tx.BtcDecode(r, 0, wire.WitnessEncoding)

	return tx
}

func (c *sigHashTestCase) ParsePrevOuts() []*wire.TxOut {
	b, _ := hex.DecodeString(c.prevOuts)
	r := bytes.NewReader(b)

	prevOutCount, _ := wire.ReadVarInt(r, 0)
	prevOuts := make([]*wire.TxOut, prevOutCount)

	for i := 0; i < int(prevOutCount); i++ {
		valueLe := make([]byte, 8)
		r.Read(valueLe[:])
		value := binary.LittleEndian.Uint64(valueLe)

		pkScriptSize, _ := wire.ReadVarInt(r, 0)
		pkScript := make([]byte, pkScriptSize)
		r.Read(pkScript)

		prevOuts[i] = &wire.TxOut{
			Value:    int64(value),
			PkScript: pkScript,
		}
	}

	return prevOuts
}

func (c *sigHashTestCase) ParseExpectedSigHash() []byte {
	b, _ := hex.DecodeString(c.expectSigHash)
	return b
}
