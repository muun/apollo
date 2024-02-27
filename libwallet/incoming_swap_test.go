package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"strconv"
	"strings"
	"testing"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	sphinx "github.com/lightningnetwork/lightning-onion"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/lightningnetwork/lnd/record"
	"github.com/lightningnetwork/lnd/tlv"
	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/muun/libwallet/hdpath"
)

func TestFulfillHtlc(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		panic(err)
	}
	err = PersistInvoiceSecrets(secrets)
	if err != nil {
		panic(err)
	}

	// stub
	swapServerPublicKey := randomBytes(32)

	invoice := secrets.Get(0)
	paymentHash := invoice.PaymentHash
	amt := int64(10000)
	lockTime := int64(1000)

	htlcKeyPath := hdpath.MustParse(invoice.keyPath).Child(htlcKeyChildIndex)
	userHtlcKey, err := userKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		panic(err)
	}
	muunHtlcKey, err := muunKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		panic(err)
	}

	htlcScript, err := createHtlcScript(
		userHtlcKey.PublicKey().Raw(),
		muunHtlcKey.PublicKey().Raw(),
		swapServerPublicKey,
		lockTime,
		paymentHash,
	)
	if err != nil {
		panic(err)
	}

	witnessHash := sha256.Sum256(htlcScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessHash[:], Regtest().network)
	if err != nil {
		t.Fatal(err)
	}

	pkScript, err := txscript.PayToAddrScript(address)
	if err != nil {
		t.Fatal(err)
	}

	prevOutHash, err := chainhash.NewHash(randomBytes(32))
	if err != nil {
		panic(err)
	}

	htlcTx := wire.NewMsgTx(1)
	htlcTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash: *prevOutHash,
		},
	})
	htlcTx.AddTxOut(&wire.TxOut{
		PkScript: pkScript,
		Value:    amt,
	})

	nodePublicKey, err := invoice.IdentityKey.key.ECPubKey()
	if err != nil {
		panic(err)
	}

	fulfillmentTx := wire.NewMsgTx(1)
	fulfillmentTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  htlcTx.TxHash(),
			Index: 0,
		},
	})

	outputPath := "m/schema:1'/recovery:1'/34/56"
	addr := newAddressAt(userKey, muunKey, outputPath, network)

	fulfillmentTx.AddTxOut(&wire.TxOut{
		PkScript: addr.ScriptAddress(),
		Value:    amt,
	})

	muunSignKey, err := muunHtlcKey.key.ECPrivKey()
	if err != nil {
		panic(err)
	}

	sigHashes := txscript.NewTxSigHashes(fulfillmentTx)
	muunSignature, err := txscript.RawTxInWitnessSignature(
		fulfillmentTx,
		sigHashes,
		0,
		amt,
		htlcScript,
		txscript.SigHashAll,
		muunSignKey,
	)
	if err != nil {
		panic(err)
	}

	swap := &IncomingSwap{
		SphinxPacket: createSphinxPacket(nodePublicKey, paymentHash, invoice.paymentSecret, amt, lockTime),
		PaymentHash:  paymentHash,
		Htlc: &IncomingSwapHtlc{
			HtlcTx:              serializeTx(htlcTx),
			ExpirationHeight:    lockTime,
			SwapServerPublicKey: swapServerPublicKey,
		},
	}

	data := &IncomingSwapFulfillmentData{
		FulfillmentTx:      serializeTx(fulfillmentTx),
		MuunSignature:      muunSignature,
		MerkleTree:         nil,
		HtlcBlock:          nil,
		ConfirmationTarget: 1,
	}

	result, err := swap.Fulfill(data, userKey, muunKey.PublicKey(), network)
	if err != nil {
		t.Fatal(err)
	}

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(result.FulfillmentTx))

	verifyInput(t, signedTx, hex.EncodeToString(swap.Htlc.HtlcTx), 0, 0)
}

func TestFulfillHtlcWithCollect(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		panic(err)
	}
	err = PersistInvoiceSecrets(secrets)
	if err != nil {
		panic(err)
	}

	// stub
	swapServerPublicKey := randomBytes(32)

	invoiceSecrets := secrets.Get(0)
	paymentHash := invoiceSecrets.PaymentHash
	amt := int64(10000)
	lockTime := int64(1000)
	collected := int64(1000)
	outputAmount := amt - collected

	htlcKeyPath := hdpath.MustParse(invoiceSecrets.keyPath).Child(htlcKeyChildIndex)
	userHtlcKey, err := userKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		panic(err)
	}
	muunHtlcKey, err := muunKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		panic(err)
	}

	htlcScript, err := createHtlcScript(
		userHtlcKey.PublicKey().Raw(),
		muunHtlcKey.PublicKey().Raw(),
		swapServerPublicKey,
		lockTime,
		paymentHash,
	)
	if err != nil {
		panic(err)
	}

	witnessHash := sha256.Sum256(htlcScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessHash[:], Regtest().network)
	if err != nil {
		t.Fatal(err)
	}

	pkScript, err := txscript.PayToAddrScript(address)
	if err != nil {
		t.Fatal(err)
	}

	prevOutHash, err := chainhash.NewHash(randomBytes(32))
	if err != nil {
		panic(err)
	}

	htlcTx := wire.NewMsgTx(1)
	htlcTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash: *prevOutHash,
		},
	})
	htlcTx.AddTxOut(&wire.TxOut{
		PkScript: pkScript,
		Value:    amt,
	})

	nodePublicKey, err := invoiceSecrets.IdentityKey.key.ECPubKey()
	if err != nil {
		panic(err)
	}

	fulfillmentTx := wire.NewMsgTx(1)
	fulfillmentTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  htlcTx.TxHash(),
			Index: 0,
		},
	})

	outputPath := "m/schema:1'/recovery:1'/34/56"
	addr := newAddressAt(userKey, muunKey, outputPath, network)

	fulfillmentTx.AddTxOut(&wire.TxOut{
		PkScript: addr.ScriptAddress(),
		Value:    outputAmount,
	})

	muunSignKey, err := muunHtlcKey.key.ECPrivKey()
	if err != nil {
		panic(err)
	}

	sigHashes := txscript.NewTxSigHashes(fulfillmentTx)
	muunSignature, err := txscript.RawTxInWitnessSignature(
		fulfillmentTx,
		sigHashes,
		0,
		amt,
		htlcScript,
		txscript.SigHashAll,
		muunSignKey,
	)
	if err != nil {
		panic(err)
	}

	swap := &IncomingSwap{
		SphinxPacket: createSphinxPacket(nodePublicKey, paymentHash, invoiceSecrets.paymentSecret, amt, lockTime),
		PaymentHash:  paymentHash,
		Htlc: &IncomingSwapHtlc{
			HtlcTx:              serializeTx(htlcTx),
			ExpirationHeight:    lockTime,
			SwapServerPublicKey: swapServerPublicKey,
		},
		CollectSat: collected,
	}

	data := &IncomingSwapFulfillmentData{
		FulfillmentTx:      serializeTx(fulfillmentTx),
		MuunSignature:      muunSignature,
		OutputVersion:      4,
		OutputPath:         outputPath,
		MerkleTree:         nil,
		HtlcBlock:          nil,
		ConfirmationTarget: 1,
	}

	result, err := swap.Fulfill(data, userKey, muunKey.PublicKey(), network)
	if err != nil {
		t.Fatal(err)
	}

	swap.CollectSat = 0
	_, err = swap.Fulfill(data, userKey, muunKey.PublicKey(), network)
	if err == nil {
		t.Fatal("expected 0 collect to fail")
	}

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(result.FulfillmentTx))

	verifyInput(t, signedTx, hex.EncodeToString(swap.Htlc.HtlcTx), 0, 0)
}

func TestVerifyFulfillable(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	generateAndPersistInvoiceSecrets := func() {
		secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
		if err != nil {
			panic(err)
		}
		err = PersistInvoiceSecrets(secrets)
		if err != nil {
			panic(err)
		}
	}

	createInvoice := func(amountSat int64) string {
		builder := InvoiceBuilder{}
		builder.Network(network)
		builder.UserKey(userKey)
		builder.AddRouteHints(&RouteHints{
			Pubkey:                    "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869fd",
			FeeBaseMsat:               1000,
			FeeProportionalMillionths: 1000,
			CltvExpiryDelta:           8,
		})
		if amountSat != 0 {
			builder.AmountSat(amountSat)
		}

	retry:
		invoice, err := builder.Build()
		if err != nil {
			panic(err)
		}
		if invoice == "" {
			generateAndPersistInvoiceSecrets()
			goto retry
		}
		return invoice
	}

	t.Run("single part payment", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)
		onion := createSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err != nil {
			t.Fatal(err)
		}
	})

	t.Run("multi part payment fails", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)

		onion := createMppSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected failure to fulfill mpp payment")
		}
	})

	t.Run("non existant invoice", func(t *testing.T) {
		swap := &IncomingSwap{
			PaymentHash: randomBytes(32),
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected failure to fulfill non existant invoice")
		}
	})

	t.Run("invalid payment secret", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, _, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)

		onion := createSphinxPacket(nodePublicKey, paymentHash, randomBytes(32), amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected error with random payment secret")
		}
	})

	t.Run("muun 2 muun with no blob", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, _, _ := getInvoiceSecrets(invoice, userKey)

		swap := &IncomingSwap{
			PaymentHash:  paymentHash,
			SphinxPacket: nil,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err != nil {
			t.Fatal(err)
		}
	})

	t.Run("invalid amount from server", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)
		onion := createSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt - 1,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected error with invalid amount")
		}
	})

	t.Run("validates amount from server", func(t *testing.T) {
		invoice := createInvoice(0)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)
		onion := createSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err != nil {
			t.Fatal(err)
		}
	})

	t.Run("validates invoice amount", func(t *testing.T) {
		invoice := createInvoice(20000)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)
		lockTime := int64(1000)
		onion := createSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected error with amount not matching invoice amount")
		}
	})

	t.Run("validates invoice amount for muun 2 muun", func(t *testing.T) {
		invoice := createInvoice(20000)
		paymentHash, _, _ := getInvoiceSecrets(invoice, userKey)
		amt := int64(10000)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err == nil {
			t.Fatal("expected error with amount not matching invoice amount")
		}
	})

	t.Run("invoice with amount", func(t *testing.T) {
		invoice := createInvoice(20000)
		paymentHash, paymentSecret, nodePublicKey := getInvoiceSecrets(invoice, userKey)
		amt := int64(20000)
		lockTime := int64(1000)
		onion := createSphinxPacket(nodePublicKey, paymentHash, paymentSecret, amt, lockTime)

		swap := &IncomingSwap{
			PaymentHash:      paymentHash,
			SphinxPacket:     onion,
			PaymentAmountSat: amt,
			// ignore the rest of the parameters
		}

		if err := swap.VerifyFulfillable(userKey, network); err != nil {
			t.Fatal(err)
		}
	})
}

func TestFulfillFailureWithoutPaymentSecret(t *testing.T) {

	setup()

	d := func(s string) []byte {
		b, _ := hex.DecodeString(s)
		return b
	}

	network := Regtest()
	swap := &IncomingSwap{
		SphinxPacket: d("0002dc29e8562cbd4961bbe76ebc847641fba878b5dda04a31d17c5c4648c4e8f614380397b83978e2f12161c7a010d494f16ca5dc96a06369a19ccfadf9ee3ec0ecdcac9479b25459d01670c629175e8cc1110f328ec6d0e21ca81c5a7f3b71023b10ca287985695fc4c757ea25c9d49bd6b4e43bb85abe043fbcb2ef473bfd1830dbdad7c3e6de26d3a703bd307cba5a33ba56d8398e22c87034b6794ecd4c2d4157a90520b78171b1860c69c302b25f7a10edf9ac3ad87d10cf7cbe8525ac4b3ebc6544787b1b010e61ab73ee86ae8752f44687753af3b31678a7fe1e85c57c6e1de33878f43ccbba1478fbd8c055a5e2c55cadcae05537f6478ba13391343c7f1063138ba9c38803ac8fd6b9eb5b5114559df1746593df1d4d9a6883f835758dc583bb9dea72ad3079df653e73efa915c629ba8056d945cf63dc316ffd118aa7e8d20430de12ac9beaf9f472b68bdf278dccd6a84f2b6c37c25ddb3abc3583094613a07f277ed80840a33ae34d62e3dd17d21e2faf82221375914444460e38ebe5ef67d9fac02de507d7964a2191b0de43c0c7115840e863f1ca03e0a5b05dedb90826b79b1b1ce5aa7666c37bae08bbe8032a82ed1d9c15df4195e408be16844dc2b5e5868a38bd560e87d629d1c6ec11e3dbb112dc1d2692ad4b7c28b5904bf49c1efcb87562f48ec5e7177f2034dadd2c08c4a02d718ffa16585738489d89f01d350123e621e4bd8927879bd3c4cccf1fe44f7b4daf4466a60b7197dbb14c5ffd23e477343fa79a8d8818804280757b1f98439749927de21545d1a9434c59c1d0e093ab3c1936b4db3b4c67dd9cae55cf2ee55066490a602a74cf88382d35db442b7e57b869fd43360ca0c9ef03bc89784e340450fcae81fb2080c97f9852124900a71bf68921e5a6e690a5ee73c266df2344106aec8de601f8a14254c97ee96dd3f858df1cb727ee51bc8ebeb6dea5253841bd2a13aeba1bc3846c9cc45d7124f9f9aa61a6c3a7b15424c5dfadfb7644392bf0843f643d97b2e08c1a3d6ebfcb7aafcd78cd2d904645cf043e1a42b60390647f24d6663fc74dc77d06bb691d12b09bb4afc3b55427f5bac76748b73b6debb17ca6bb890f2005f39e714aa0e7a584e57a41a78f1d3f4981ce4e22a49caa389360eabc9f623b923c864eb74a2a860a061d6ecbe6f4c55596907ba342836c7607117f405e098af1f73b8ae2542a59d30c58fca8ee37c6482bd87069b142e692f54a04fd6d3a5e22595eb2de31c830cea4395b085b7c8725971df657c5af5501fa8cc9cefda4f1ae8862b6229ed74b045e17587f68ab55c9176c256c69564274502d0ec6e5e3be8ea93e14428d328963ca4671ee2f629ae8f2c2ff8f2b2145f218d8a3707715bdfa5b2bb5211b9cd8775e33ce5546f618bc998b5953c5d2a2f7932873fd248be3a504ce7f7d4b731bfb4fea363e5e281ff3c314b997d8c89d90c8bf15d983da26e75bf52e98b92d108e6f4aee0b25561d0ce8f22a8400b2085e713d909c20b2c84d5ba36dbe94f324690ab207070bfb7247510e78263989dc04669ea273ca44d2de31aa8a950bc120fcec0c627ad78b59f635ddd657d97d56fcc9ebef32b3ee1051e003c0b617a1196d6c387f014fd47e7f1c64b27d43cadfaf25a7849a77392a63470665e5e3bb0c28b66b9de938c805fab01de62cd63b0d200f97156236fcd412f1eadc125371bd09726e65da8ee8e77e7fa0070bb4f6090a2afd7a33e3d37aff7a5dac62830a7f79aa28f6bce305fc6eb96dd53cd2448b618bdadfc79dcee815d6dd6935d9cece06f810df6cbd529b01361d97f3c50d749739d9598edd53c9bd984a5348a5345c25c13fc7c6d48b7412f4ab6de74e6b7fd4945f710562c312a2903680c387a7364920e435db7777fe66b60a49adb656cdd12f"),
		PaymentHash:  d("31b35302d3e842a363f8992e423910bfb655b9cd6325b67f5c469fa8f2c4e55b"),
		Htlc: &IncomingSwapHtlc{
			HtlcTx:              d("02000000000101896c8b88d8219cc7dae111558626c952da6fc2a542f7db970e8af745c4678bdb0000000000feffffff02d006032a01000000160014b710e26258f27a99807e2a09bf39b5d3588c561b089d0000000000002200208fb1ed3841bee4385ba4efe1a8aff0943b3b1eeadada45e4784f54e2efa1f30a0247304402205e6a82391804b8bc483f6d9d44bdcd7afb477f66c4c794872735447f1dd883480220626fc746386f8afed04a43776d661bab1d610cdebcb5d03c7d594b0edd3612ed0121037d4c78fdce4b13788efb012a68834da3a75f6ac153f55edf22fadc09e6d4f67700000000"),
			ExpirationHeight:    401,
			SwapServerPublicKey: d("028b7c740b590012eaffef072675baaa95aee39508fd049ed1cd698ee26ce33f02"),
		},
	}
	data := &IncomingSwapFulfillmentData{
		FulfillmentTx:      d("0100000001a2b209d88daaa2b9fedc8217904b75934d280f889cd64db243c530dbd72a9b670100000000ffffffff0110270000000000002200209c58b43eff77533a3a056046ee4cb5044bb0eeb74635ebb8cc03048b3720716b00000000"),
		MuunSignature:      d("30450221008c40c9ef1613cfa500c52531b9fd0b7212f562e425dcdc4358cc3a6de25e11940220717ab86c13cb645dd2e694c3b4e5fd0e81e84f00ed8380570ab33a19fed0547201"),
		OutputVersion:      4,
		OutputPath:         "m/schema:1\\'/recovery:1\\'/change:0/3",
		MerkleTree:         d(""),
		HtlcBlock:          d(""),
		ConfirmationTarget: 0,
	}

	userKey, _ := NewHDPrivateKeyFromString("tprv8eNitriyeyGgaAe7teh17j8mvqN3MuzkFy5TzdfS4KUATjgdP29jN7w9A8iQ5PDUZMqsb2aiJjEgjuPGCRjoDbJsCZ5iFGpb4uJCXkksjXM", "m/schema:1'/recovery:1'", network)
	muunKey, _ := NewHDPublicKeyFromString("tpubDBYMnFoxYLdMBZThTk4uARTe4kGPeEYWdKcaEzaUxt1cesetnxtTqmAxVkzDRou51emWytommyLWcF91SdF5KecA6Ja8oHK1FF7d5U2hMxX", "m/schema:1'/recovery:1'", network)

	invoice := &InvoiceSecrets{
		preimage:      d("52441108d7144b82ed13a18b7572fa78fa6f6a3f85fdbf4752dcce985430e43c"),
		paymentSecret: d("79d011595d443897b46c2811bd4e5aa7f3fa225b880249edb64b52601aa7f963"),
		keyPath:       "m/schema:1'/recovery:1'/invoices:4/1159744029/738246992",
		PaymentHash:   d("31b35302d3e842a363f8992e423910bfb655b9cd6325b67f5c469fa8f2c4e55b"),
		IdentityKey:   nil,
		UserHtlcKey:   nil,
		MuunHtlcKey:   nil,
		ShortChanId:   123,
	}

	PersistInvoiceSecrets(&InvoiceSecretsList{secrets: []*InvoiceSecrets{invoice}})

	result, err := swap.Fulfill(data, userKey, muunKey, network)
	if err == nil || result != nil {
		t.Fatal("expected failure due to missing payment secret")
	}
}

// TestFulfillWithIncorrectPaymentSecret tests that payment secret sphinx validation works and its enforced. The way we
// do this is by having every other piece of data be correct and consistent and only the payment secret being invalid.
// When trying to fulfill(), the function should fail with an error signalling a payment secret mismatch. Technically,
// we should be modifying the sphinx packet to contain an incorrect payment secret instead of checking against an
// invalid payment secret provided by the app/invoice. But our current approach is WAY easier and saved us a lot of
// time :).
func TestFulfillWithIncorrectPaymentSecret(t *testing.T) {

	setup()

	d := func(s string) []byte {
		b, _ := hex.DecodeString(s)
		return b
	}

	parseInt64 := func(s string) int64 {
		res, _ := strconv.ParseInt(s, 10, 64)
		return res
	}

	// Note: for this test, we want to avoid depending on the code that we use to generate sphinx packets in other tests
	// (e.g createSphinxPacket). So, the following values are taken from a data dump of an incoming payment attempt in
	// a local (e.g regtest) env in device emulator.

	network := Regtest()
	swap := &IncomingSwap{
		SphinxPacket: d("000315be7c15b19fb4a5c4f8654fa2527f766b0a4db6c9ebc32fc47aed1a07127fc3506162074aeee6d9c60c763b9f867ebe083c383b8467617dcc4fa961be959909200a56d8628591eb253bffeb5eab444b4e483c627a618228b8a2cf4f336bc4035e42453133d17e447227534af81d814d5fc0b444fe23bfea587fb28abbad185998f7fc58e753ff7f8017b3cd525a65c15956ea47792208717564da215253287ca36bf1f2b632a8f32c89ce6aa2347a4f668ce3d0c87240a830483976638c289e6973be4910a24c2a3c16f0694bedc2f7bbaa1cdc132d749bab063c2854be6ac05e105d7db09ebf080b7dd2684c6223a84cc82853cee6f55bb4e910eb0e70a178c4974df7d19c819856ab13899ce69f23f1cf764608482a7ae6ec241cfae0244219c9d65543992f30e2550c16834a8f5eb0c432fc05132dd972b0d092570bcd2c421695f9b0b31465c84ea8b7bc87a5ba8b8d9ae69766e049231021e5dd3335ad60379f08ba51a1b4b2643e16bf7492e4a14f40f0505dbfcb7185a1ff2ab31be46a3554495db3d9a050f14d8faa3ff8a822cb09ca6e48077c65a1f3da1e1ab6efd5d871bbfd02ab4f274046e0c0666831b62ca42d754c06aad1e7bd807a9cb2a6a8963dfa8027f68411f1e52f89f2f32a24a1db0ae46f695c7a0d0217d68e03e902ed0382f99e30b514c890dd370b193bd6da19623473dd9772a5a1119dbe2d525831b112bb7aefa3ed3e71f5f9bf10d163d5d5d93fac9b8513a9844a1230bfcd1e3021d562e30bcd5e2c46ab479a0b401d66ec72a6280fccd150e9a8d786a3e6896bb17d31f9cfe1148a9787901d1d6fa84b061342505039a965c3f8fa88a0faaec728f32f21c83c5acf5b7276d511455909588612af064914b67bef705846e271a8f898b13815a127aa63aa5b66f5610a227b6601be526417e92b88732fa52997951125b225e6a2e1e1cafc6ac18a93f3fa810e6cfd594ff57b0061304d777ebd8a40ca8eac19877e3df7419a8355122d403dedbef8b110c61b3b2821fc1788609cd0f20bc8be162303cd936640179665b89747e39c847cff19f32a002abb63557292ccef8a4847bb5c02394f947a2d0bd4df00b82203a482954e5814c2ac2ce7839f1eeb5428bfd992bfff4af2dde537505891c3c1d345a6e750de22d3b6e50023bf5172f415d5618f67097c20bb016fdeb87cdea66ea88adeb8b5d8b014d86364b35c915740d96bfe8432fcc9f21ecc33d29402d84e405106c65e572d68ac313a34a4ff117b4eac946736287c528bd490ad60f5fff9dc18ef8c3de80e18207ccbdc7a5abc6f73b79ec098ea8f41323032dc6ed62584a2c4dd2e4c4b6e76b88dc26d33279da47aa91e1eebb44dcdb5ead73c5bf6f17584142dca009ff29bcc46e291c59fd24d3b3b8a0e2157df61ffea5265c589de2c62f33a66ee1408e2b7ae2c17168a4183eb3bf392f70437c2c65c37d0d22e861c4caab58c89705c1ee2a9ad45e338347f524071875dc48c3a23f67b6e670b228caf945d5b8d7a389b3766ac01e40b79791fd667f342604cab6374bf4bd9d795a4f181d7f48192e947da557fc5280f224da63e8fff64e862984183c4465d5beaad464fcd24122c38a24a83b31ab31a618efb2a56af109ba7c5c1b1911620165ac01088b1559fa02d49017c635cd8e3a26e0d9699c7ff4048389521add8fdb72a439959364db2e612964ab9483a812c25dd35d2360d0a8855205ee72ecc4542d42a1b25b5d12fdc94c77ba1a7479d23854838c79f1a7b83774907f8be0912744a256c1934c9734de55f65446d7a197753b2f4d37374a3637e27a134872f7e70cfc8b0a1c205e00c2c93c6867f2f70f335a6bffac8b80461ada6f6e0e0bba69be6fb8dbf5141318b43e7dbdd3e58776621eb3a07df4d5a47584bc503ca68e1"),
		PaymentHash:  d("c7165cd3692877f5a85c51d834730dddffa1493117273926a20310e18b44523d"),
		Htlc: &IncomingSwapHtlc{
			HtlcTx:              d("0200000000010151ca4ece06e7ec3e0458dfd8bba0c58ad53c8e296fdcbb08de15f8b17a2419e10000000000ffffffff0834ca010000000000220020a0b8c170b680c76e4655d80e4e206206b4fb365674fd752590862445499dae2040420f00000000001600148b194ab0e7019f8eddfba7f14b00045a82bd8bf040420f0000000000160014d64044cd7e17e204a2ebc5737fa9862e1ab6b71e40420f000000000016001434353a4568fe6edaa2c83647fe0c591ef61421f840420f0000000000160014bce7c77a9b648e69f0292331ece4436150ce593340420f000000000016001439fbdcce9849d9a913a40911c318322e4bfda65340420f0000000000160014193a6bd1cd36d510a245c9959c507bfdcde45c04e015a829010000001600143e5eb692c5d6703672fac7b86bae54ead1b074c50247304402207a7ac24c0e43123b7f6d4d27fcb21fdc3c7ed2cb1c601e03136e4f988ad0468d0220440e596e241728073df049ed0dda504f55b435ba4535c2e5f0844c636f3da65501210313d8559514b06cca0da351b0d05222acfc92ff3f90cc7ddef32a648a80790a0b00000000"),
			ExpirationHeight:    504,
			SwapServerPublicKey: d("028b7c740b590012eaffef072675baaa95aee39508fd049ed1cd698ee26ce33f02"),
		},
	}

	data := &IncomingSwapFulfillmentData{
		FulfillmentTx:      d("01000000013754eeb4d1e71094e2470024163f073ce0e6c4f1b16ded51d1792255f4c8e3ed0000000000ffffffff01a086010000000000225120005914f986cb6749440e0e77367bac6c6e53d814449a2fd7443474aab61606f300000000"),
		MuunSignature:      d("304402206a1cfc3d01a8ca050967e5dddff87984ebafe390a61dc5225044c2ce22b02fae022071887ef3f13bd1b4cca33ffb1a217de5fb92b3bb74df398f8508fec0b37dfa9f01"),
		OutputVersion:      5,
		OutputPath:         "m/schema:1\\'/recovery:1\\'/change:0/5",
		MerkleTree:         d(""),
		HtlcBlock:          d(""),
		ConfirmationTarget: 0,
	}

	userKey, _ := NewHDPrivateKeyFromString("tprv8fAB8ynEKVR4LdJUc6ryH3u2tqJF8wZJH2rzRprZ6YhqdFHi5HnP1fYRuoHfos9RQZ1bkxsyP8oHENfiezvAp4dFj83rYbBGkQHSwbhqiDW", "m/schema:1'/recovery:1'", network)
	muunKey, _ := NewHDPublicKeyFromString("tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8", "m/schema:1'/recovery:1'", network)

	invoice := &InvoiceSecrets{
		preimage:      d("e28dd8e23e3f427190104373c71f46db31efa665612f670610afe378a1713100"),
		paymentSecret: d("e28dd8e23e3f427190104373c71f46db31efa665612f670610afe378a1713100"), // INVALID
		keyPath:       "m/schema:1'/recovery:1'/invoices:4/2036904351/908182055",
		PaymentHash:   d("c7165cd3692877f5a85c51d834730dddffa1493117273926a20310e18b44523d"),
		IdentityKey:   nil,
		UserHtlcKey:   nil,
		MuunHtlcKey:   nil,
		ShortChanId:   parseInt64("15120913803481186240"),
	}

	PersistInvoiceSecrets(&InvoiceSecretsList{secrets: []*InvoiceSecrets{invoice}})

	_, err := swap.Fulfill(data, userKey, muunKey, network)

	// We used an invalid secret, sphinx validation HAS to fail
	if err == nil || !strings.Contains(err.Error(), "sphinx payment secret does not match") {
		t.Fatal("expected failure due to invalid payment secret")
	}
}

func TestFulfillWithHardwiredData(t *testing.T) {

	setup()

	d := func(s string) []byte {
		b, _ := hex.DecodeString(s)
		return b
	}

	parseInt64 := func(s string) int64 {
		res, _ := strconv.ParseInt(s, 10, 64)
		return res
	}

	// Note: for this test, we want to avoid depending on the code that we use to generate sphinx packets in other tests
	// (e.g createSphinxPacket). So, the following values are taken from a data dump of an incoming payment attempt in
	// a local (e.g regtest) env in device emulator.

	network := Regtest()
	swap := &IncomingSwap{
		SphinxPacket: d("000315be7c15b19fb4a5c4f8654fa2527f766b0a4db6c9ebc32fc47aed1a07127fc3506162074aeee6d9c60c763b9f867ebe083c383b8467617dcc4fa961be959909200a56d8628591eb253bffeb5eab444b4e483c627a618228b8a2cf4f336bc4035e42453133d17e447227534af81d814d5fc0b444fe23bfea587fb28abbad185998f7fc58e753ff7f8017b3cd525a65c15956ea47792208717564da215253287ca36bf1f2b632a8f32c89ce6aa2347a4f668ce3d0c87240a830483976638c289e6973be4910a24c2a3c16f0694bedc2f7bbaa1cdc132d749bab063c2854be6ac05e105d7db09ebf080b7dd2684c6223a84cc82853cee6f55bb4e910eb0e70a178c4974df7d19c819856ab13899ce69f23f1cf764608482a7ae6ec241cfae0244219c9d65543992f30e2550c16834a8f5eb0c432fc05132dd972b0d092570bcd2c421695f9b0b31465c84ea8b7bc87a5ba8b8d9ae69766e049231021e5dd3335ad60379f08ba51a1b4b2643e16bf7492e4a14f40f0505dbfcb7185a1ff2ab31be46a3554495db3d9a050f14d8faa3ff8a822cb09ca6e48077c65a1f3da1e1ab6efd5d871bbfd02ab4f274046e0c0666831b62ca42d754c06aad1e7bd807a9cb2a6a8963dfa8027f68411f1e52f89f2f32a24a1db0ae46f695c7a0d0217d68e03e902ed0382f99e30b514c890dd370b193bd6da19623473dd9772a5a1119dbe2d525831b112bb7aefa3ed3e71f5f9bf10d163d5d5d93fac9b8513a9844a1230bfcd1e3021d562e30bcd5e2c46ab479a0b401d66ec72a6280fccd150e9a8d786a3e6896bb17d31f9cfe1148a9787901d1d6fa84b061342505039a965c3f8fa88a0faaec728f32f21c83c5acf5b7276d511455909588612af064914b67bef705846e271a8f898b13815a127aa63aa5b66f5610a227b6601be526417e92b88732fa52997951125b225e6a2e1e1cafc6ac18a93f3fa810e6cfd594ff57b0061304d777ebd8a40ca8eac19877e3df7419a8355122d403dedbef8b110c61b3b2821fc1788609cd0f20bc8be162303cd936640179665b89747e39c847cff19f32a002abb63557292ccef8a4847bb5c02394f947a2d0bd4df00b82203a482954e5814c2ac2ce7839f1eeb5428bfd992bfff4af2dde537505891c3c1d345a6e750de22d3b6e50023bf5172f415d5618f67097c20bb016fdeb87cdea66ea88adeb8b5d8b014d86364b35c915740d96bfe8432fcc9f21ecc33d29402d84e405106c65e572d68ac313a34a4ff117b4eac946736287c528bd490ad60f5fff9dc18ef8c3de80e18207ccbdc7a5abc6f73b79ec098ea8f41323032dc6ed62584a2c4dd2e4c4b6e76b88dc26d33279da47aa91e1eebb44dcdb5ead73c5bf6f17584142dca009ff29bcc46e291c59fd24d3b3b8a0e2157df61ffea5265c589de2c62f33a66ee1408e2b7ae2c17168a4183eb3bf392f70437c2c65c37d0d22e861c4caab58c89705c1ee2a9ad45e338347f524071875dc48c3a23f67b6e670b228caf945d5b8d7a389b3766ac01e40b79791fd667f342604cab6374bf4bd9d795a4f181d7f48192e947da557fc5280f224da63e8fff64e862984183c4465d5beaad464fcd24122c38a24a83b31ab31a618efb2a56af109ba7c5c1b1911620165ac01088b1559fa02d49017c635cd8e3a26e0d9699c7ff4048389521add8fdb72a439959364db2e612964ab9483a812c25dd35d2360d0a8855205ee72ecc4542d42a1b25b5d12fdc94c77ba1a7479d23854838c79f1a7b83774907f8be0912744a256c1934c9734de55f65446d7a197753b2f4d37374a3637e27a134872f7e70cfc8b0a1c205e00c2c93c6867f2f70f335a6bffac8b80461ada6f6e0e0bba69be6fb8dbf5141318b43e7dbdd3e58776621eb3a07df4d5a47584bc503ca68e1"),
		PaymentHash:  d("c7165cd3692877f5a85c51d834730dddffa1493117273926a20310e18b44523d"),
		Htlc: &IncomingSwapHtlc{
			HtlcTx:              d("0200000000010151ca4ece06e7ec3e0458dfd8bba0c58ad53c8e296fdcbb08de15f8b17a2419e10000000000ffffffff0834ca010000000000220020a0b8c170b680c76e4655d80e4e206206b4fb365674fd752590862445499dae2040420f00000000001600148b194ab0e7019f8eddfba7f14b00045a82bd8bf040420f0000000000160014d64044cd7e17e204a2ebc5737fa9862e1ab6b71e40420f000000000016001434353a4568fe6edaa2c83647fe0c591ef61421f840420f0000000000160014bce7c77a9b648e69f0292331ece4436150ce593340420f000000000016001439fbdcce9849d9a913a40911c318322e4bfda65340420f0000000000160014193a6bd1cd36d510a245c9959c507bfdcde45c04e015a829010000001600143e5eb692c5d6703672fac7b86bae54ead1b074c50247304402207a7ac24c0e43123b7f6d4d27fcb21fdc3c7ed2cb1c601e03136e4f988ad0468d0220440e596e241728073df049ed0dda504f55b435ba4535c2e5f0844c636f3da65501210313d8559514b06cca0da351b0d05222acfc92ff3f90cc7ddef32a648a80790a0b00000000"),
			ExpirationHeight:    504,
			SwapServerPublicKey: d("028b7c740b590012eaffef072675baaa95aee39508fd049ed1cd698ee26ce33f02"),
		},
	}
	htlcTxIndex := 0 // Required for verifying input script at end of test
	data := &IncomingSwapFulfillmentData{
		FulfillmentTx:      d("01000000013754eeb4d1e71094e2470024163f073ce0e6c4f1b16ded51d1792255f4c8e3ed0000000000ffffffff01a086010000000000225120005914f986cb6749440e0e77367bac6c6e53d814449a2fd7443474aab61606f300000000"),
		MuunSignature:      d("304402206a1cfc3d01a8ca050967e5dddff87984ebafe390a61dc5225044c2ce22b02fae022071887ef3f13bd1b4cca33ffb1a217de5fb92b3bb74df398f8508fec0b37dfa9f01"),
		OutputVersion:      5,
		OutputPath:         "m/schema:1\\'/recovery:1\\'/change:0/5",
		MerkleTree:         d(""),
		HtlcBlock:          d(""),
		ConfirmationTarget: 0,
	}

	userKey, _ := NewHDPrivateKeyFromString("tprv8fAB8ynEKVR4LdJUc6ryH3u2tqJF8wZJH2rzRprZ6YhqdFHi5HnP1fYRuoHfos9RQZ1bkxsyP8oHENfiezvAp4dFj83rYbBGkQHSwbhqiDW", "m/schema:1'/recovery:1'", network)
	muunKey, _ := NewHDPublicKeyFromString("tpubDBZaivUL3Hv8r25JDupShPuWVkGcwM7NgbMBwkhQLfWu18iBbyQCbRdyg1wRMjoWdZN7Afg3F25zs4c8E6Q4VJrGqAw51DJeqacTFABV9u8", "m/schema:1'/recovery:1'", network)

	invoice := &InvoiceSecrets{
		preimage:      d("e28dd8e23e3f427190104373c71f46db31efa665612f670610afe378a1713100"),
		paymentSecret: d("fbcb6bda97ab5f75da45e6efda921903acf25dd5138edac1dfde9fcecefcf617"),
		keyPath:       "m/schema:1'/recovery:1'/invoices:4/2036904351/908182055",
		PaymentHash:   d("c7165cd3692877f5a85c51d834730dddffa1493117273926a20310e18b44523d"),
		IdentityKey:   nil,
		UserHtlcKey:   nil,
		MuunHtlcKey:   nil,
		ShortChanId:   parseInt64("15120913803481186240"),
	}

	PersistInvoiceSecrets(&InvoiceSecretsList{secrets: []*InvoiceSecrets{invoice}})

	result, err := swap.Fulfill(data, userKey, muunKey, network)
	if err != nil {
		t.Fatal(err)
	}

	htlcTx := wire.NewMsgTx(2)
	htlcTx.Deserialize(bytes.NewReader(swap.Htlc.HtlcTx))

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(result.FulfillmentTx))

	verifyInput(t, signedTx, hex.EncodeToString(swap.Htlc.HtlcTx), htlcTxIndex, 0)
}

func TestFulfillFullDebt(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		panic(err)
	}
	err = PersistInvoiceSecrets(secrets)
	if err != nil {
		panic(err)
	}

	invoice := secrets.Get(0)

	swap := &IncomingSwap{
		PaymentHash: invoice.PaymentHash,
	}

	result, err := swap.FulfillFullDebt()
	if err != nil {
		t.Fatal(err)
	}

	if result.FulfillmentTx != nil {
		t.Fatal("expected FulfillmentTx to be nil")
	}
	if result.Preimage == nil {
		t.Fatal("expected preimage to be non-nil")
	}
}

func createSphinxPacket(nodePublicKey *btcec.PublicKey, paymentHash, paymentSecret []byte, amt, lockTime int64) []byte {
	var paymentPath sphinx.PaymentPath
	paymentPath[0].NodePub = *nodePublicKey

	var secret [32]byte
	copy(secret[:], paymentSecret)
	uintAmount := uint64(amt * 1000) // msat are expected
	uintLocktime := uint32(lockTime)
	tlvRecords := []tlv.Record{
		record.NewAmtToFwdRecord(&uintAmount),
		record.NewLockTimeRecord(&uintLocktime),
		record.NewMPP(lnwire.MilliSatoshi(uintAmount), secret).Record(),
	}

	b := &bytes.Buffer{}
	tlv.MustNewStream(tlvRecords...).Encode(b)
	hopPayload, err := sphinx.NewHopPayload(nil, b.Bytes())
	if err != nil {
		panic(err)
	}
	paymentPath[0].HopPayload = hopPayload

	ephemeralKey, err := btcec.NewPrivateKey(btcec.S256())
	if err != nil {
		panic(err)
	}

	pkt, err := sphinx.NewOnionPacket(
		&paymentPath, ephemeralKey, paymentHash, sphinx.BlankPacketFiller)
	if err != nil {
		panic(err)
	}

	var buf bytes.Buffer
	err = pkt.Encode(&buf)
	if err != nil {
		panic(err)
	}

	return buf.Bytes()
}

func createMppSphinxPacket(
	nodePublicKey *btcec.PublicKey,
	paymentHash, paymentSecret []byte,
	amt, lockTime int64,
) []byte {

	var paymentPath sphinx.PaymentPath
	paymentPath[0].NodePub = *nodePublicKey

	var secret [32]byte
	copy(secret[:], paymentSecret)
	uintAmount := uint64(amt * 1000) // msat are expected
	uintLocktime := uint32(lockTime)
	uintFwdAmount := uintAmount / 2
	tlvRecords := []tlv.Record{
		record.NewAmtToFwdRecord(&uintFwdAmount),
		record.NewLockTimeRecord(&uintLocktime),
		record.NewMPP(lnwire.MilliSatoshi(uintAmount), secret).Record(),
	}

	b := &bytes.Buffer{}
	tlv.MustNewStream(tlvRecords...).Encode(b)
	hopPayload, err := sphinx.NewHopPayload(nil, b.Bytes())
	if err != nil {
		panic(err)
	}
	paymentPath[0].HopPayload = hopPayload

	ephemeralKey, err := btcec.NewPrivateKey(btcec.S256())
	if err != nil {
		panic(err)
	}

	pkt, err := sphinx.NewOnionPacket(
		&paymentPath, ephemeralKey, paymentHash, sphinx.BlankPacketFiller)
	if err != nil {
		panic(err)
	}

	var buf bytes.Buffer
	err = pkt.Encode(&buf)
	if err != nil {
		panic(err)
	}

	return buf.Bytes()
}

func newAddressAt(userKey, muunKey *HDPrivateKey, keyPath string, network *Network) btcutil.Address {
	userPublicKey, err := userKey.PublicKey().DeriveTo(keyPath)
	if err != nil {
		panic(err)
	}
	muunPublicKey, err := muunKey.PublicKey().DeriveTo(keyPath)
	if err != nil {
		panic(err)
	}
	muunAddr, err := CreateAddressV4(userPublicKey, muunPublicKey)
	if err != nil {
		panic(err)
	}
	addr, err := btcutil.DecodeAddress(muunAddr.Address(), network.network)
	if err != nil {
		panic(err)
	}
	return addr
}

func serializeTx(tx *wire.MsgTx) []byte {
	var buf bytes.Buffer
	err := tx.Serialize(&buf)
	if err != nil {
		panic(err)
	}
	return buf.Bytes()
}

func getInvoiceSecrets(invoice string, userKey *HDPrivateKey) (paymentHash []byte, paymentSecret []byte, identityKey *btcec.PublicKey) {
	db, err := openDB()
	if err != nil {
		panic(err)
	}
	defer db.Close()

	payReq, err := zpay32.Decode(invoice, network.network)
	if err != nil {
		panic(err)
	}
	dbInvoice, err := db.FindByPaymentHash(payReq.PaymentHash[:])
	if err != nil {
		panic(err)
	}

	paymentHash = payReq.PaymentHash[:]
	paymentSecret = dbInvoice.PaymentSecret

	keyPath := hdpath.MustParse(dbInvoice.KeyPath).Child(identityKeyChildIndex)
	key, err := userKey.DeriveTo(keyPath.String())
	if err != nil {
		panic(err)
	}
	identityKey, err = key.key.ECPubKey()
	if err != nil {
		panic(err)
	}
	return
}
