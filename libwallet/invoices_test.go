package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec"
	"github.com/lightningnetwork/lnd/record"
	"github.com/lightningnetwork/lnd/tlv"

	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	sphinx "github.com/lightningnetwork/lightning-onion"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/muun/libwallet/hdpath"
)

func TestInvoiceSecrets(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		t.Fatal(err)
	}
	if secrets.Length() != 5 {
		t.Fatalf("expected 5 new secrets, got %d", secrets.Length())
	}

	err = PersistInvoiceSecrets(secrets)
	if err != nil {
		t.Fatal(err)
	}

	// try to generate more secrets
	moreSecrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		t.Fatal(err)
	}
	if moreSecrets.Length() != 0 {
		t.Fatal("expected no new secrets to be created")
	}

	routeHints := &RouteHints{
		Pubkey:                    "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869fd",
		FeeBaseMsat:               1000,
		FeeProportionalMillionths: 1000,
		CltvExpiryDelta:           8,
	}

	invoice, err := CreateInvoice(network, userKey, routeHints, &InvoiceOptions{
		AmountSat:   1000,
		Description: "hello world",
	})
	if err != nil {
		t.Fatal(err)
	}
	if invoice == "" {
		t.Fatal("expected non-empty invoice string")
	}

	payreq, err := zpay32.Decode(invoice, network.network)
	if err != nil {
		t.Fatal(err)
	}
	if !payreq.Features.HasFeature(lnwire.TLVOnionPayloadOptional) {
		t.Fatal("expected invoice to have var onion optin feature")
	}
	if !payreq.Features.HasFeature(lnwire.PaymentAddrOptional) {
		t.Fatal("expected invoice to have payment secret feature")
	}
	if payreq.MilliSat.ToSatoshis() != btcutil.Amount(1000) {
		t.Fatalf("expected invoice amount to be 1000 sats, got %v", payreq.MilliSat)
	}
	if payreq.Description == nil || *payreq.Description != "hello world" {
		t.Fatalf("expected payment description to match, got %v", payreq.Description)
	}
	if payreq.MinFinalCLTVExpiry() != 144 {
		t.Fatalf("expected min final CLTV expiry to be 144, got %v", payreq.MinFinalCLTVExpiry())
	}
	if payreq.PaymentAddr == nil {
		t.Fatalf("expected payment addr to be non-nil")
	}
	if len(payreq.RouteHints) == 0 {
		t.Fatalf("expected invoice to contain route hints")
	}
	hopHints := payreq.RouteHints[0]
	if len(hopHints) != 1 {
		t.Fatalf("expected invoice route hints to contain exactly 1 hop hint")
	}
	if hopHints[0].ChannelID&(1<<63) == 0 {
		t.Fatal("invalid short channel id in hophints")
	}
	if hopHints[0].FeeBaseMSat != 1000 {
		t.Fatalf("expected fee base to be 1000 msat, got %v instead", hopHints[0].FeeBaseMSat)
	}
	if hopHints[0].FeeProportionalMillionths != 1000 {
		t.Fatalf("expected fee proportional millionths to be 1000, got %v instead", hopHints[0].FeeProportionalMillionths)
	}
	if hopHints[0].CLTVExpiryDelta != 8 {
		t.Fatalf("expected CLTV expiry delta to be 8, got %v instead", hopHints[0].CLTVExpiryDelta)
	}

	invoice2, err := CreateInvoice(network, userKey, routeHints, &InvoiceOptions{})
	if err != nil {
		t.Fatal(err)
	}

	payreq2, err := zpay32.Decode(invoice2, network.network)
	if err != nil {
		t.Fatal(err)
	}

	if payreq.PaymentHash == payreq2.PaymentHash {
		t.Fatal("successive invoice payment hashes should be different")
	}

}

func TestVerifyAndFulfillHtlc(t *testing.T) {
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
		FulfillmentTx:       serializeTx(fulfillmentTx),
		MuunSignature:       muunSignature,
		Sphinx:              createSphinxPacket(nodePublicKey, paymentHash, invoice.paymentSecret, amt, lockTime),
		PaymentHash:         paymentHash,
		BlockHeight:         123456,
		HtlcTx:              serializeTx(htlcTx),
		OutputVersion:       4,
		OutputPath:          outputPath,
		SwapServerPublicKey: hex.EncodeToString(swapServerPublicKey),
		MerkleTree:          nil,
		HtlcExpiration:      lockTime,
		HtlcBlock:           nil,
		ConfirmationTarget:  1,
	}

	signedTxBytes, err := swap.VerifyAndFulfill(userKey, muunKey.PublicKey(), network)
	if err != nil {
		t.Fatal(err)
	}

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(signedTxBytes))

	verifyInput(t, signedTx, hex.EncodeToString(swap.HtlcTx), 0, 0)
}

func TestVerifyAndFulfillHtlcWithCollect(t *testing.T) {
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
		FulfillmentTx:       serializeTx(fulfillmentTx),
		MuunSignature:       muunSignature,
		Sphinx:              createSphinxPacket(nodePublicKey, paymentHash, invoiceSecrets.paymentSecret, amt, lockTime),
		PaymentHash:         paymentHash,
		BlockHeight:         123456,
		HtlcTx:              serializeTx(htlcTx),
		OutputVersion:       4,
		OutputPath:          outputPath,
		SwapServerPublicKey: hex.EncodeToString(swapServerPublicKey),
		MerkleTree:          nil,
		HtlcExpiration:      lockTime,
		HtlcBlock:           nil,
		ConfirmationTarget:  1,
		CollectInSats:       collected,
	}

	signedTxBytes, err := swap.VerifyAndFulfill(userKey, muunKey.PublicKey(), network)
	if err != nil {
		t.Fatal(err)
	}

	swap.CollectInSats = 0
	_, err = swap.VerifyAndFulfill(userKey, muunKey.PublicKey(), network)
	if err == nil {
		t.Fatal("expected 0 collect to fail")
	}

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(signedTxBytes))

	verifyInput(t, signedTx, hex.EncodeToString(swap.HtlcTx), 0, 0)
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

func serializeTx(tx *wire.MsgTx) []byte {
	var buf bytes.Buffer
	err := tx.Serialize(&buf)
	if err != nil {
		panic(err)
	}
	return buf.Bytes()
}

func TestVerifyAndFulfillWithHardwiredData(t *testing.T) {

	setup()

	d := func(s string) []byte {
		b, _ := hex.DecodeString(s)
		return b
	}

	network := Regtest()
	swap := &IncomingSwap{
		FulfillmentTx:       d("0100000001a2b209d88daaa2b9fedc8217904b75934d280f889cd64db243c530dbd72a9b670100000000ffffffff0110270000000000002200209c58b43eff77533a3a056046ee4cb5044bb0eeb74635ebb8cc03048b3720716b00000000"),
		MuunSignature:       d("30450221008c40c9ef1613cfa500c52531b9fd0b7212f562e425dcdc4358cc3a6de25e11940220717ab86c13cb645dd2e694c3b4e5fd0e81e84f00ed8380570ab33a19fed0547201"),
		Sphinx:              d("0002dc29e8562cbd4961bbe76ebc847641fba878b5dda04a31d17c5c4648c4e8f614380397b83978e2f12161c7a010d494f16ca5dc96a06369a19ccfadf9ee3ec0ecdcac9479b25459d01670c629175e8cc1110f328ec6d0e21ca81c5a7f3b71023b10ca287985695fc4c757ea25c9d49bd6b4e43bb85abe043fbcb2ef473bfd1830dbdad7c3e6de26d3a703bd307cba5a33ba56d8398e22c87034b6794ecd4c2d4157a90520b78171b1860c69c302b25f7a10edf9ac3ad87d10cf7cbe8525ac4b3ebc6544787b1b010e61ab73ee86ae8752f44687753af3b31678a7fe1e85c57c6e1de33878f43ccbba1478fbd8c055a5e2c55cadcae05537f6478ba13391343c7f1063138ba9c38803ac8fd6b9eb5b5114559df1746593df1d4d9a6883f835758dc583bb9dea72ad3079df653e73efa915c629ba8056d945cf63dc316ffd118aa7e8d20430de12ac9beaf9f472b68bdf278dccd6a84f2b6c37c25ddb3abc3583094613a07f277ed80840a33ae34d62e3dd17d21e2faf82221375914444460e38ebe5ef67d9fac02de507d7964a2191b0de43c0c7115840e863f1ca03e0a5b05dedb90826b79b1b1ce5aa7666c37bae08bbe8032a82ed1d9c15df4195e408be16844dc2b5e5868a38bd560e87d629d1c6ec11e3dbb112dc1d2692ad4b7c28b5904bf49c1efcb87562f48ec5e7177f2034dadd2c08c4a02d718ffa16585738489d89f01d350123e621e4bd8927879bd3c4cccf1fe44f7b4daf4466a60b7197dbb14c5ffd23e477343fa79a8d8818804280757b1f98439749927de21545d1a9434c59c1d0e093ab3c1936b4db3b4c67dd9cae55cf2ee55066490a602a74cf88382d35db442b7e57b869fd43360ca0c9ef03bc89784e340450fcae81fb2080c97f9852124900a71bf68921e5a6e690a5ee73c266df2344106aec8de601f8a14254c97ee96dd3f858df1cb727ee51bc8ebeb6dea5253841bd2a13aeba1bc3846c9cc45d7124f9f9aa61a6c3a7b15424c5dfadfb7644392bf0843f643d97b2e08c1a3d6ebfcb7aafcd78cd2d904645cf043e1a42b60390647f24d6663fc74dc77d06bb691d12b09bb4afc3b55427f5bac76748b73b6debb17ca6bb890f2005f39e714aa0e7a584e57a41a78f1d3f4981ce4e22a49caa389360eabc9f623b923c864eb74a2a860a061d6ecbe6f4c55596907ba342836c7607117f405e098af1f73b8ae2542a59d30c58fca8ee37c6482bd87069b142e692f54a04fd6d3a5e22595eb2de31c830cea4395b085b7c8725971df657c5af5501fa8cc9cefda4f1ae8862b6229ed74b045e17587f68ab55c9176c256c69564274502d0ec6e5e3be8ea93e14428d328963ca4671ee2f629ae8f2c2ff8f2b2145f218d8a3707715bdfa5b2bb5211b9cd8775e33ce5546f618bc998b5953c5d2a2f7932873fd248be3a504ce7f7d4b731bfb4fea363e5e281ff3c314b997d8c89d90c8bf15d983da26e75bf52e98b92d108e6f4aee0b25561d0ce8f22a8400b2085e713d909c20b2c84d5ba36dbe94f324690ab207070bfb7247510e78263989dc04669ea273ca44d2de31aa8a950bc120fcec0c627ad78b59f635ddd657d97d56fcc9ebef32b3ee1051e003c0b617a1196d6c387f014fd47e7f1c64b27d43cadfaf25a7849a77392a63470665e5e3bb0c28b66b9de938c805fab01de62cd63b0d200f97156236fcd412f1eadc125371bd09726e65da8ee8e77e7fa0070bb4f6090a2afd7a33e3d37aff7a5dac62830a7f79aa28f6bce305fc6eb96dd53cd2448b618bdadfc79dcee815d6dd6935d9cece06f810df6cbd529b01361d97f3c50d749739d9598edd53c9bd984a5348a5345c25c13fc7c6d48b7412f4ab6de74e6b7fd4945f710562c312a2903680c387a7364920e435db7777fe66b60a49adb656cdd12f"),
		PaymentHash:         d("31b35302d3e842a363f8992e423910bfb655b9cd6325b67f5c469fa8f2c4e55b"),
		BlockHeight:         0,
		HtlcTx:              d("02000000000101896c8b88d8219cc7dae111558626c952da6fc2a542f7db970e8af745c4678bdb0000000000feffffff02d006032a01000000160014b710e26258f27a99807e2a09bf39b5d3588c561b089d0000000000002200208fb1ed3841bee4385ba4efe1a8aff0943b3b1eeadada45e4784f54e2efa1f30a0247304402205e6a82391804b8bc483f6d9d44bdcd7afb477f66c4c794872735447f1dd883480220626fc746386f8afed04a43776d661bab1d610cdebcb5d03c7d594b0edd3612ed0121037d4c78fdce4b13788efb012a68834da3a75f6ac153f55edf22fadc09e6d4f67700000000"),
		OutputVersion:       4,
		OutputPath:          "m/schema:1\\'/recovery:1\\'/change:0/3",
		SwapServerPublicKey: "028b7c740b590012eaffef072675baaa95aee39508fd049ed1cd698ee26ce33f02",
		MerkleTree:          d(""),
		HtlcExpiration:      401,
		HtlcBlock:           d(""),
		ConfirmationTarget:  0,
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

	tx, err := swap.VerifyAndFulfill(userKey, muunKey, network)
	if err != nil {
		t.Fatal(err)
	}

	htlcTx := wire.NewMsgTx(2)
	htlcTx.Deserialize(bytes.NewReader(swap.HtlcTx))

	signedTx := wire.NewMsgTx(2)
	signedTx.Deserialize(bytes.NewReader(tx))

	verifyInput(t, signedTx, hex.EncodeToString(swap.HtlcTx), 1, 0)

}
