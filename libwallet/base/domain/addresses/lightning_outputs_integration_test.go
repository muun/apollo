package addresses_test

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/base/domain/addresses"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/test/testbitcoind"
	"github.com/muun/libwallet/platform/test/testbtcscript"
	"github.com/muun/libwallet/platform/test/testsuite"
)

// LightningOutputsTestSuite broadcasts every spending path of the lightning
// channel outputs to a real bitcoin core node.
type LightningOutputsTestSuite struct {
	testsuite.BaseIntegrationSuite
	node *testbitcoind.Client
}

func TestLightningOutputs_Integration(t *testing.T) {
	testsuite.Run(t, new(LightningOutputsTestSuite))
}

const (
	integrationPath = "m/schema:1'/recovery:1'/change:0/0"

	// Small relative timelock so delayed paths mature within the tests.
	integrationDelay = int64(5)

	integrationFundingAmount = btcutil.Amount(100_000)
	integrationFee           = int64(10_000)
)

func (s *LightningOutputsTestSuite) SetupSuite() {
	s.BaseIntegrationSuite.SetupSuite()

	s.node = testbitcoind.NewClient(s.T())
	s.node.EnsureMatureCoins()
	addresses.SetSpendDelaysForTest(s.T(), integrationDelay)
}

func (s *LightningOutputsTestSuite) SetupTest() {
	s.BaseIntegrationSuite.SetupTest()

	s.node.StartNewTest(s.T())
}

func (s *LightningOutputsTestSuite) TearDownTest() {
	// Generate a block to clean up the mempool.
	s.node.GenerateBlocks(1)
}

// integrationKeyTriple returns the deterministic participant keys at the
// schema base path as a public key triple, from masters built with a single
// repeated byte.
func (s *LightningOutputsTestSuite) integrationKeyTriple() *hd_keys.PublicKeyTriple {
	s.T().Helper()

	publicKeys := make([]*hd_keys.HDPublicKey, 3)
	for i, fill := range []byte{0x01, 0x03, 0x04} {
		master := hd_keys.NewMasterHDPrivateKeyFromBytes(
			bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
		)
		derived, err := master.DeriveTo("m/schema:1'/recovery:1'")
		s.NoError(err)
		publicKeys[i] = derived.PublicKey()
	}
	return hd_keys.NewPublicKeyTriple(publicKeys[0], publicKeys[1], publicKeys[2])
}

// integrationPrivKey returns the private key of the fill-based master at the
// schema base path.
func (s *LightningOutputsTestSuite) integrationPrivKey(fill byte) *hd_keys.HDPrivateKey {
	s.T().Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo("m/schema:1'/recovery:1'")
	s.NoError(err)
	return derived
}

// integrationECPrivKey returns the private key of the fill-based master
// derived at integrationPath.
func (s *LightningOutputsTestSuite) integrationECPrivKey(fill byte) *btcec.PrivateKey {
	s.T().Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo(integrationPath)
	s.NoError(err)
	privKey, err := derived.ECPrivKey()
	s.NoError(err)
	return privKey
}

// A watchtower sweeps two revoked to_server outputs. Each input is pre-signed
// on a transaction carrying only that input, the way a sweep payload is
// assembled: SIGHASH_ALL | SIGHASH_ANYONECANPAY commits to every output but
// not to the other inputs, so the sweep output is fixed before signing.
func (s *LightningOutputsTestSuite) TestToServerRevocationSweep() {
	userPrivKey := s.integrationPrivKey(0x01)
	revocationPrivKey := testbtcscript.PrivKeyFromByte(0x02)

	output, err := addresses.NewToServerOutput(
		s.integrationKeyTriple(), integrationPath, revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	firstFundingTx, firstVout := s.fundOutputOnNode(output)
	secondFundingTx, secondVout := s.fundOutputOnNode(output)

	// The sweep pays every input to one output, fixed before any input is signed.
	destScript, err := txscript.PayToAddrScript(s.node.GetNewAddress("receive"))
	s.NoError(err)
	sweepOutput := wire.NewTxOut(
		firstFundingTx.TxOut[firstVout].Value+
			secondFundingTx.TxOut[secondVout].Value-
			integrationFee,
		destScript,
	)

	firstWitness := s.preSignSweepInput(
		output, firstFundingTx, firstVout, sweepOutput, userPrivKey, revocationPrivKey,
	)
	secondWitness := s.preSignSweepInput(
		output, secondFundingTx, secondVout, sweepOutput, userPrivKey, revocationPrivKey,
	)

	// The watchtower assembles the sweep from the pre-signed inputs. It has no
	// relative timelock: the revocation path is not delayed.
	sweepTx := wire.NewMsgTx(2)
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  firstFundingTx.TxHash(),
			Index: uint32(firstVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  firstWitness,
	})
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  secondFundingTx.TxHash(),
			Index: uint32(secondVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  secondWitness,
	})
	sweepTx.AddTxOut(sweepOutput)

	_, err = s.node.SendRawTransaction(s.txToHex(sweepTx))
	s.NoError(err)
}

// sweepSigningTx builds a transaction carrying only the funding input and the
// sweep output, with a fetcher for the input's prevout.
func (s *LightningOutputsTestSuite) sweepSigningTx(
	fundingTx *wire.MsgTx,
	vout int,
	sweepOutput *wire.TxOut,
) (*wire.MsgTx, *txscript.MultiPrevOutFetcher) {
	s.T().Helper()

	signingTx := wire.NewMsgTx(2)
	signingTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{Hash: fundingTx.TxHash(), Index: uint32(vout)},
		Sequence:         wire.MaxTxInSequenceNum,
	})
	signingTx.AddTxOut(sweepOutput)

	prevOuts := txscript.NewMultiPrevOutFetcher(map[wire.OutPoint]*wire.TxOut{
		signingTx.TxIn[0].PreviousOutPoint: fundingTx.TxOut[vout],
	})
	return signingTx, prevOuts
}

// preSignSweepInput signs a revocation sweep of one funding output on a
// transaction carrying only that input and the sweep output, the way each
// revoked output is pre-signed without knowing the rest of the sweep.
func (s *LightningOutputsTestSuite) preSignSweepInput(
	output *addresses.ToServerOutput,
	fundingTx *wire.MsgTx,
	vout int,
	sweepOutput *wire.TxOut,
	userPrivKey *hd_keys.HDPrivateKey,
	revocationPrivKey *btcec.PrivateKey,
) wire.TxWitness {
	s.T().Helper()

	signingTx, prevOuts := s.sweepSigningTx(fundingTx, vout, sweepOutput)

	finalSignature, err := output.ComputeRevocationFullSignature(
		0,
		signingTx,
		txscript.NewTxSigHashes(signingTx, prevOuts),
		prevOuts,
		userPrivKey,
		revocationPrivKey,
	)
	s.NoError(err)

	witness, err := output.AssembleRevocationWitness(finalSignature)
	s.NoError(err)
	return witness
}

// Spending the to_server script path is rejected before the relative timelock matures and
// accepted after it.
func (s *LightningOutputsTestSuite) TestToServerNonCollaborative() {
	// A different revocation key than the penalty test: each channel state has its own.
	revocationPrivKey := testbtcscript.PrivKeyFromByte(0x05)
	lightningServerPrivKey := s.integrationECPrivKey(0x04)

	output, err := addresses.NewToServerOutput(
		s.integrationKeyTriple(), integrationPath, revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	claimTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	addresses.SignNonCollaborativeForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		lightningServerPrivKey,
		output,
	)

	// The funding output has one confirmation: the claim must not get in before the delay.
	_, err = s.node.SendRawTransaction(s.txToHex(claimTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := addresses.LongestToServerNonCollaborativeWitness()
	s.Require().NoError(err)
	s.LessOrEqual(claimTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// The lightning server spends a to_server_immediately output with its key alone.
func (s *LightningOutputsTestSuite) TestToServerImmediatelySpend() {
	output, err := addresses.NewToServerImmediatelyOutput(
		s.integrationKeyTriple(), integrationPath,
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerImmediatelyForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		fundingTx.TxOut[vout],
		s.integrationECPrivKey(0x04),
	)

	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := addresses.LongestToServerImmediatelyWitness()
	s.Require().NoError(err)
	s.LessOrEqual(spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// fundOutputOnNode funds one channel state's output on the node.
func (s *LightningOutputsTestSuite) fundOutputOnNode(
	output interface {
		Address() (*addresses.WalletAddress, error)
	},
) (*wire.MsgTx, int) {
	s.T().Helper()

	walletAddress, err := output.Address()
	s.NoError(err)
	address, err := btcutil.DecodeAddress(
		walletAddress.Address(), &chaincfg.RegressionNetParams,
	)
	s.NoError(err)
	pkScript, err := txscript.PayToAddrScript(address)
	s.NoError(err)

	txID := s.node.SendToAddress(address, integrationFundingAmount.ToBTC())
	s.node.GenerateBlocks(1)

	fundingTx := s.node.DecodeTx(s.node.GetRawTransaction(txID))
	for vout, out := range fundingTx.TxOut {
		if bytes.Equal(out.PkScript, pkScript) {
			return &fundingTx, vout
		}
	}
	s.FailNowf("missing funding output", "funding tx %s has no output paying %x", txID, pkScript)
	return nil, 0
}

// spendOutput builds a tx spending the given output of prevTx back to the node's wallet, with the
// given input sequence.
func (s *LightningOutputsTestSuite) spendOutput(
	prevTx *wire.MsgTx,
	vout int,
	sequence uint32,
) *wire.MsgTx {
	s.T().Helper()

	destScript, err := txscript.PayToAddrScript(s.node.GetNewAddress("receive"))
	s.NoError(err)

	tx := wire.NewMsgTx(2)
	tx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{Hash: prevTx.TxHash(), Index: uint32(vout)},
		Sequence:         sequence,
	})
	tx.AddTxOut(wire.NewTxOut(prevTx.TxOut[vout].Value-integrationFee, destScript))
	return tx
}

func (s *LightningOutputsTestSuite) txToHex(tx *wire.MsgTx) string {
	s.T().Helper()

	var buf bytes.Buffer
	s.NoError(tx.Serialize(&buf))
	return hex.EncodeToString(buf.Bytes())
}

// A watchtower sweeps a revoked to_server_htlc output through the revocation
// leaf together with a revoked to_server output spent through the key path,
// each input pre-signed on a transaction carrying only that input and the
// fixed sweep output.
func (s *LightningOutputsTestSuite) TestToServerHTLCRevocationSweep() {
	paymentHash := sha256.Sum256([]byte("htlc penalty"))
	keys, htlcOutput := s.htlcOutputForTest(paymentHash[:])
	toServerOutput, err := addresses.NewToServerOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	htlcFundingTx, htlcVout := s.fundOutputOnNode(htlcOutput)
	toServerFundingTx, toServerVout := s.fundOutputOnNode(toServerOutput)

	// The sweep pays every input to one output, fixed before any input is signed.
	destScript, err := txscript.PayToAddrScript(s.node.GetNewAddress("receive"))
	s.NoError(err)
	sweepOutput := wire.NewTxOut(
		htlcFundingTx.TxOut[htlcVout].Value+
			toServerFundingTx.TxOut[toServerVout].Value-
			integrationFee,
		destScript,
	)

	htlcWitness := s.preSignHTLCSweepInput(
		htlcOutput, htlcFundingTx, htlcVout, sweepOutput,
		keys.userPrivKey, keys.revocationPrivKey,
	)
	toServerWitness := s.preSignSweepInput(
		toServerOutput, toServerFundingTx, toServerVout, sweepOutput,
		keys.userPrivKey, keys.revocationPrivKey,
	)

	// The watchtower assembles the sweep from the pre-signed inputs. It has no
	// relative timelock: the revocation paths are not delayed.
	sweepTx := wire.NewMsgTx(2)
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  htlcFundingTx.TxHash(),
			Index: uint32(htlcVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  htlcWitness,
	})
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  toServerFundingTx.TxHash(),
			Index: uint32(toServerVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  toServerWitness,
	})
	sweepTx.AddTxOut(sweepOutput)

	_, err = s.node.SendRawTransaction(s.txToHex(sweepTx))
	s.NoError(err)
}

// preSignHTLCSweepInput signs a revocation sweep of one to_server_htlc funding
// output on a transaction carrying only that input and the sweep output, the
// way each revoked output is pre-signed without knowing the rest of the
// sweep.
func (s *LightningOutputsTestSuite) preSignHTLCSweepInput(
	output *addresses.ToServerHTLCOutput,
	fundingTx *wire.MsgTx,
	vout int,
	sweepOutput *wire.TxOut,
	userPrivKey *hd_keys.HDPrivateKey,
	revocationPrivKey *btcec.PrivateKey,
) wire.TxWitness {
	s.T().Helper()

	signingTx, prevOuts := s.sweepSigningTx(fundingTx, vout, sweepOutput)

	finalSignature, err := output.ComputeRevocationFullSignature(
		0,
		signingTx,
		txscript.NewTxSigHashes(signingTx, prevOuts),
		prevOuts,
		userPrivKey,
		revocationPrivKey,
	)
	s.NoError(err)
	witness, err := output.AssembleRevocationWitness(finalSignature)
	s.NoError(err)
	return witness
}

// Spending through the non-collaborative leaf is rejected before the expiration and
// accepted after it.
func (s *LightningOutputsTestSuite) TestToServerHTLCNonCollaborative() {
	paymentHash := sha256.Sum256([]byte("htlc non-collaborative"))
	keys, output := s.htlcOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := addresses.ComputeToServerHTLCNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
		s.integrationKeyTriple(),
		integrationPath,
	)
	s.NoError(err)

	// The spender learns the branch hash from the state metadata.
	branchHash, err := output.MetadataOutputData()
	s.NoError(err)

	witness, err := addresses.AssembleToServerHTLCNonCollaborativeWitness(
		finalSignature,
		s.integrationKeyTriple(),
		integrationPath,
		branchHash,
	)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the expiration.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server claims a to_server_htlc output through the preimage leaf.
func (s *LightningOutputsTestSuite) TestToServerHTLCPreimage() {
	preimage := bytes.Repeat([]byte{0xcc}, 32)
	paymentHash := sha256.Sum256(preimage)
	keys, output := s.htlcOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	// The preimage spend has no relative timelock.
	claimTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCPreimageForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
		preimage,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)
}

// The three participants spend a to_server_htlc output collaboratively through the key path.
func (s *LightningOutputsTestSuite) TestToServerHTLCCollaborative() {
	paymentHash := sha256.Sum256([]byte("htlc key path"))
	keys, output := s.htlcOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// testHTLCParticipants holds the private keys of one to_server_htlc test
// output: the wallet keys at the schema base path and the revocation key.
type testHTLCParticipants struct {
	userPrivKey            *hd_keys.HDPrivateKey
	cosignerPrivKey        *hd_keys.HDPrivateKey
	lightningServerPrivKey *hd_keys.HDPrivateKey
	revocationPrivKey      *btcec.PrivateKey
}

// newTestHTLCParticipants builds the participants from deterministic keys.
func (s *LightningOutputsTestSuite) newTestHTLCParticipants() testHTLCParticipants {
	s.T().Helper()

	return testHTLCParticipants{
		userPrivKey:            s.integrationPrivKey(0x01),
		cosignerPrivKey:        s.integrationPrivKey(0x03),
		lightningServerPrivKey: s.integrationPrivKey(0x04),
		revocationPrivKey:      testbtcscript.PrivKeyFromByte(0x06),
	}
}

// htlcOutputForTest builds a ToServerHTLCOutput from deterministic keys and the given
// payment hash, expiring after the integration delay.
func (s *LightningOutputsTestSuite) htlcOutputForTest(
	paymentHash []byte,
) (testHTLCParticipants, *addresses.ToServerHTLCOutput) {
	s.T().Helper()

	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToServerHTLCOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHash,
	)
	s.NoError(err)
	return keys, output
}

// A watchtower sweeps a revoked to_client_htlc output through the revocation
// leaf together with a revoked to_server output spent through the key path,
// each input pre-signed on a transaction carrying only that input and the
// fixed sweep output.
func (s *LightningOutputsTestSuite) TestToClientHTLCRevocationSweep() {
	paymentHash := sha256.Sum256([]byte("client htlc penalty"))
	keys, htlcOutput := s.toClientHTLCOutputForTest([][]byte{paymentHash[:]})
	toServerOutput, err := addresses.NewToServerOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	htlcFundingTx, htlcVout := s.fundOutputOnNode(htlcOutput)
	toServerFundingTx, toServerVout := s.fundOutputOnNode(toServerOutput)

	// The sweep pays every input to one output, fixed before any input is signed.
	destScript, err := txscript.PayToAddrScript(s.node.GetNewAddress("receive"))
	s.NoError(err)
	sweepOutput := wire.NewTxOut(
		htlcFundingTx.TxOut[htlcVout].Value+
			toServerFundingTx.TxOut[toServerVout].Value-
			integrationFee,
		destScript,
	)

	htlcWitness := s.preSignClientHTLCSweepInput(
		htlcOutput, htlcFundingTx, htlcVout, sweepOutput,
		keys.userPrivKey, keys.revocationPrivKey,
	)
	toServerWitness := s.preSignSweepInput(
		toServerOutput, toServerFundingTx, toServerVout, sweepOutput,
		keys.userPrivKey, keys.revocationPrivKey,
	)

	// The watchtower assembles the sweep from the pre-signed inputs. It has no
	// relative timelock: the revocation paths are not delayed.
	sweepTx := wire.NewMsgTx(2)
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  htlcFundingTx.TxHash(),
			Index: uint32(htlcVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  htlcWitness,
	})
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  toServerFundingTx.TxHash(),
			Index: uint32(toServerVout),
		},
		Sequence: wire.MaxTxInSequenceNum,
		Witness:  toServerWitness,
	})
	sweepTx.AddTxOut(sweepOutput)

	_, err = s.node.SendRawTransaction(s.txToHex(sweepTx))
	s.NoError(err)
}

// preSignClientHTLCSweepInput signs a revocation sweep of one to_client_htlc
// funding output on a transaction carrying only that input and the sweep
// output, the way each revoked output is pre-signed without knowing the rest
// of the sweep.
func (s *LightningOutputsTestSuite) preSignClientHTLCSweepInput(
	output *addresses.ToClientHTLCOutput,
	fundingTx *wire.MsgTx,
	vout int,
	sweepOutput *wire.TxOut,
	userPrivKey *hd_keys.HDPrivateKey,
	revocationPrivKey *btcec.PrivateKey,
) wire.TxWitness {
	s.T().Helper()

	signingTx, prevOuts := s.sweepSigningTx(fundingTx, vout, sweepOutput)

	finalSignature, err := output.ComputeRevocationFullSignature(
		0,
		signingTx,
		txscript.NewTxSigHashes(signingTx, prevOuts),
		prevOuts,
		userPrivKey,
		revocationPrivKey,
	)
	s.NoError(err)
	witness, err := output.AssembleRevocationWitness(finalSignature)
	s.NoError(err)
	return witness
}

// Spending through the non-collaborative leaf is rejected before the expiration and
// accepted after it.
func (s *LightningOutputsTestSuite) TestToClientHTLCNonCollaborative() {
	paymentHash := sha256.Sum256([]byte("client htlc non-collaborative"))
	keys, output := s.toClientHTLCOutputForTest([][]byte{paymentHash[:]})

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := addresses.ComputeToClientHTLCNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
		s.integrationKeyTriple(),
		integrationPath,
	)
	s.NoError(err)

	// The spender learns the branch hash from the state metadata.
	branchHash, err := output.MetadataOutputData()
	s.NoError(err)

	witness, err := addresses.AssembleToClientHTLCNonCollaborativeWitness(
		finalSignature,
		s.integrationKeyTriple(),
		integrationPath,
		branchHash,
	)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the expiration.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The user claims a to_client_htlc output holding two payments through the preimage leaf.
func (s *LightningOutputsTestSuite) TestToClientHTLCPreimage() {
	firstPreimage := bytes.Repeat([]byte{0xdd}, 32)
	firstPaymentHash := sha256.Sum256(firstPreimage)
	secondPreimage := bytes.Repeat([]byte{0xee}, 32)
	secondPaymentHash := sha256.Sum256(secondPreimage)
	keys, output := s.toClientHTLCOutputForTest(
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

	fundingTx, vout := s.fundOutputOnNode(output)

	// The preimage spend has no relative timelock.
	claimTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientHTLCPreimageForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
		[][]byte{firstPreimage, secondPreimage},
	)

	_, err := s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)
}

// The three participants spend a to_client_htlc output collaboratively through the key path.
func (s *LightningOutputsTestSuite) TestToClientHTLCCollaborative() {
	paymentHash := sha256.Sum256([]byte("client htlc key path"))
	keys, output := s.toClientHTLCOutputForTest([][]byte{paymentHash[:]})

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientHTLCCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// toClientHTLCOutputForTest builds a ToClientHTLCOutput from deterministic keys and the
// given payment hashes, expiring after the integration delay.
func (s *LightningOutputsTestSuite) toClientHTLCOutputForTest(
	paymentHashes [][]byte,
) (testHTLCParticipants, *addresses.ToClientHTLCOutput) {
	s.T().Helper()

	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientHTLCOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHashes,
	)
	s.NoError(err)
	return keys, output
}

// Spending the to_client_withdrawal non-collaborative leaf is rejected before the
// relative timelock matures and accepted after it.
func (s *LightningOutputsTestSuite) TestToClientWithdrawalNonCollaborative() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientWithdrawalOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := output.ComputeNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
	)
	s.NoError(err)
	witness, err := output.AssembleNonCollaborativeWitness(finalSignature)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the delay.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The three participants spend a to_client_withdrawal output collaboratively
// through the key path.
func (s *LightningOutputsTestSuite) TestToClientWithdrawalCollaborative() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientWithdrawalOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientWithdrawalCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// Spending the to_server_htlc_withdrawal non-collaborative leaf is rejected until
// both the HTLC timeout height and the relative delay are reached.
func (s *LightningOutputsTestSuite) TestToServerHTLCWithdrawalNonCollaborative() {
	keys := s.newTestHTLCParticipants()
	paymentHash := sha256.Sum256([]byte("htlc withdrawal non-collaborative"))

	// The timeout height lands two blocks after the funding confirmation, so
	// it gates the spend before the relative delay does.
	htlcTimeout := int64(s.node.GetBlockCount()) + 3

	output, err := addresses.NewToServerHTLCWithdrawalOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHash[:],
		htlcTimeout,
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	spendTx.LockTime = uint32(htlcTimeout)
	finalSignature, err := addresses.ComputeToServerHTLCWithdrawalNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
		s.integrationKeyTriple(),
		integrationPath,
		htlcTimeout,
	)
	s.NoError(err)

	// The spender learns the branch hash and the HTLC timeout from the state
	// metadata.
	metadata, err := output.MetadataOutputData()
	s.NoError(err)
	branchHash := metadata[:sha256.Size]
	metadataTimeout := int64(binary.LittleEndian.Uint32(metadata[sha256.Size:]))

	witness, err := addresses.AssembleToServerHTLCWithdrawalNonCollaborativeWitness(
		finalSignature,
		s.integrationKeyTriple(),
		integrationPath,
		metadataTimeout,
		branchHash,
	)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// Below the timeout height the locktime keeps the spend out.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-final")

	// At the timeout height the relative delay still keeps it out.
	s.node.GenerateBlocks(2)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 3)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server claims a to_server_htlc_withdrawal output through the
// preimage leaf on its own.
func (s *LightningOutputsTestSuite) TestToServerHTLCWithdrawalPreimage() {
	keys, preimage, output := s.htlcWithdrawalOutputForTest()

	fundingTx, vout := s.fundOutputOnNode(output)

	// The preimage spend has no timelocks.
	claimTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCWithdrawalPreimageForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		preimage,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)
}

// The three participants spend a to_server_htlc_withdrawal output
// collaboratively through the key path.
func (s *LightningOutputsTestSuite) TestToServerHTLCWithdrawalCollaborative() {
	keys, _, output := s.htlcWithdrawalOutputForTest()

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCWithdrawalCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server spends a revoked htlc withdrawal through the revocation
// leaf using the revealed revocation key.
func (s *LightningOutputsTestSuite) TestToServerHTLCWithdrawalRevocation() {
	keys, _, output := s.htlcWithdrawalOutputForTest()

	fundingTx, vout := s.fundOutputOnNode(output)

	// The revocation spend has no timelocks.
	penaltyTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCWithdrawalRevocationForTest(
		s.T(),
		0,
		penaltyTx,
		testbtcscript.SigHashesFor(penaltyTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		keys.revocationPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(penaltyTx))
	s.NoError(err)
}

// htlcWithdrawalOutputForTest builds a ToServerHTLCWithdrawalOutput from
// deterministic keys, its payment preimage, and the integration delay. The
// timeout height is only exercised by the non-collaborative test, which builds its
// own output.
func (s *LightningOutputsTestSuite) htlcWithdrawalOutputForTest() (
	testHTLCParticipants, []byte, *addresses.ToServerHTLCWithdrawalOutput,
) {
	s.T().Helper()

	const htlcTimeoutHeight = int64(1_000)

	keys := s.newTestHTLCParticipants()
	preimage := bytes.Repeat([]byte{0xdd}, 32)
	paymentHash := sha256.Sum256(preimage)
	output, err := addresses.NewToServerHTLCWithdrawalOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHash[:],
		htlcTimeoutHeight,
	)
	s.NoError(err)
	return keys, preimage, output
}

// The lightning server spends a revoked withdrawal through the revocation leaf
// using the revealed revocation key.
func (s *LightningOutputsTestSuite) TestToClientWithdrawalRevocation() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientWithdrawalOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	// The revocation spend has no relative timelock.
	penaltyTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientWithdrawalRevocationForTest(
		s.T(),
		0,
		penaltyTx,
		testbtcscript.SigHashesFor(penaltyTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		keys.revocationPrivKey,
	)

	_, err = s.node.SendRawTransaction(s.txToHex(penaltyTx))
	s.NoError(err)
}

// Spending the to_client_revocable non-collaborative leaf is rejected before
// the relative timelock matures and accepted after it.
func (s *LightningOutputsTestSuite) TestToClientRevocableNonCollaborative() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientRevocableOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := output.ComputeNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
	)
	s.NoError(err)
	witness, err := output.AssembleNonCollaborativeWitness(finalSignature)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the delay.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The three participants spend a to_client_revocable output collaboratively
// through the key path.
func (s *LightningOutputsTestSuite) TestToClientRevocableCollaborative() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientRevocableOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientRevocableCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server spends a revoked to_client_revocable output through the
// revocation leaf using the revealed revocation key.
func (s *LightningOutputsTestSuite) TestToClientRevocableRevocation() {
	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientRevocableOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
	)
	s.NoError(err)

	fundingTx, vout := s.fundOutputOnNode(output)

	// The revocation spend has no relative timelock.
	penaltyTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientRevocableRevocationForTest(
		s.T(),
		0,
		penaltyTx,
		testbtcscript.SigHashesFor(penaltyTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		keys.revocationPrivKey,
	)

	_, err = s.node.SendRawTransaction(s.txToHex(penaltyTx))
	s.NoError(err)
}

// Spending the to_client_htlc_enforcement non-collaborative leaf is rejected
// before the relative timelock matures and accepted after it.
func (s *LightningOutputsTestSuite) TestToClientHTLCEnforcementNonCollaborative() {
	paymentHash := sha256.Sum256([]byte("enforcement non-collaborative"))
	keys, output := s.enforcementOutputForTest([][]byte{paymentHash[:]})

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := addresses.ComputeToClientHTLCEnforcementNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
		s.integrationKeyTriple(),
		integrationPath,
	)
	s.NoError(err)

	// The spender learns the branch hash from the state metadata.
	branchHash, err := output.MetadataOutputData()
	s.NoError(err)

	witness, err := addresses.AssembleToClientHTLCEnforcementNonCollaborativeWitness(
		finalSignature,
		s.integrationKeyTriple(),
		integrationPath,
		branchHash,
	)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the expiration.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The user claims a to_client_htlc_enforcement output holding two payments
// through the preimage leaf.
func (s *LightningOutputsTestSuite) TestToClientHTLCEnforcementPreimage() {
	firstPreimage := bytes.Repeat([]byte{0xf0}, 32)
	firstPaymentHash := sha256.Sum256(firstPreimage)
	secondPreimage := bytes.Repeat([]byte{0xf1}, 32)
	secondPaymentHash := sha256.Sum256(secondPreimage)
	keys, output := s.enforcementOutputForTest(
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

	fundingTx, vout := s.fundOutputOnNode(output)

	// The preimage spend has no relative timelock.
	claimTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientHTLCEnforcementPreimageForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
		[][]byte{firstPreimage, secondPreimage},
	)

	_, err := s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)
}

// The three participants spend a to_client_htlc_enforcement output
// collaboratively through the key path.
func (s *LightningOutputsTestSuite) TestToClientHTLCEnforcementCollaborative() {
	paymentHash := sha256.Sum256([]byte("enforcement key path"))
	keys, output := s.enforcementOutputForTest([][]byte{paymentHash[:]})

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientHTLCEnforcementCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server spends a revoked to_client_htlc_enforcement output
// through the revocation leaf using the revealed revocation key.
func (s *LightningOutputsTestSuite) TestToClientHTLCEnforcementRevocation() {
	paymentHash := sha256.Sum256([]byte("enforcement penalty"))
	keys, output := s.enforcementOutputForTest([][]byte{paymentHash[:]})

	fundingTx, vout := s.fundOutputOnNode(output)

	// The revocation spend has no relative timelock.
	penaltyTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToClientHTLCEnforcementRevocationForTest(
		s.T(),
		0,
		penaltyTx,
		testbtcscript.SigHashesFor(penaltyTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		keys.revocationPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(penaltyTx))
	s.NoError(err)
}

// enforcementOutputForTest builds a ToClientHTLCEnforcementOutput from
// deterministic keys and the given payment hashes, expiring after the
// integration delay.
func (s *LightningOutputsTestSuite) enforcementOutputForTest(
	paymentHashes [][]byte,
) (testHTLCParticipants, *addresses.ToClientHTLCEnforcementOutput) {
	s.T().Helper()

	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToClientHTLCEnforcementOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHashes,
	)
	s.NoError(err)
	return keys, output
}

// Spending the to_server_htlc_enforcement non-collaborative leaf is rejected
// before the relative timelock matures and accepted after it.
func (s *LightningOutputsTestSuite) TestToServerHTLCEnforcementNonCollaborative() {
	paymentHash := sha256.Sum256([]byte("server enforcement non-collaborative"))
	keys, output := s.serverEnforcementOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, uint32(integrationDelay))
	finalSignature, err := addresses.ComputeToServerHTLCEnforcementNonCollaborativeFullSignature(
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		keys.userPrivKey,
		keys.cosignerPrivKey,
		s.integrationKeyTriple(),
		integrationPath,
	)
	s.NoError(err)

	// The spender learns the branch hash from the state metadata.
	branchHash, err := output.MetadataOutputData()
	s.NoError(err)

	witness, err := addresses.AssembleToServerHTLCEnforcementNonCollaborativeWitness(
		finalSignature,
		s.integrationKeyTriple(),
		integrationPath,
		branchHash,
	)
	s.NoError(err)
	spendTx.TxIn[0].Witness = witness

	// The funding output has one confirmation: the spend must not get in before the expiration.
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.ErrorContains(err, "non-BIP68-final")

	s.node.GenerateBlocks(int(integrationDelay) - 1)
	_, err = s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server claims a to_server_htlc_enforcement output through the
// preimage leaf.
func (s *LightningOutputsTestSuite) TestToServerHTLCEnforcementPreimage() {
	preimage := bytes.Repeat([]byte{0xf2}, 32)
	paymentHash := sha256.Sum256(preimage)
	keys, output := s.serverEnforcementOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	// The preimage spend has no relative timelock.
	claimTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCEnforcementPreimageForTest(
		s.T(),
		0,
		claimTx,
		testbtcscript.SigHashesFor(claimTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
		preimage,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(claimTx))
	s.NoError(err)
}

// The three participants spend a to_server_htlc_enforcement output
// collaboratively through the key path.
func (s *LightningOutputsTestSuite) TestToServerHTLCEnforcementCollaborative() {
	paymentHash := sha256.Sum256([]byte("server enforcement key path"))
	keys, output := s.serverEnforcementOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	spendTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCEnforcementCollaborativeSpendForTest(
		s.T(),
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, fundingTx.TxOut[vout]),
		output,
		keys.userPrivKey,
		keys.cosignerPrivKey,
		keys.lightningServerPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(spendTx))
	s.NoError(err)
}

// The lightning server spends a revoked to_server_htlc_enforcement output
// through the revocation leaf using the revealed revocation key.
func (s *LightningOutputsTestSuite) TestToServerHTLCEnforcementRevocation() {
	paymentHash := sha256.Sum256([]byte("server enforcement penalty"))
	keys, output := s.serverEnforcementOutputForTest(paymentHash[:])

	fundingTx, vout := s.fundOutputOnNode(output)

	// The revocation spend has no relative timelock.
	penaltyTx := s.spendOutput(fundingTx, vout, wire.MaxTxInSequenceNum)
	addresses.SignToServerHTLCEnforcementRevocationForTest(
		s.T(),
		0,
		penaltyTx,
		testbtcscript.SigHashesFor(penaltyTx, fundingTx.TxOut[vout]),
		output,
		keys.lightningServerPrivKey,
		keys.revocationPrivKey,
	)

	_, err := s.node.SendRawTransaction(s.txToHex(penaltyTx))
	s.NoError(err)
}

// serverEnforcementOutputForTest builds a ToServerHTLCEnforcementOutput from
// deterministic keys and the given payment hash, expiring after the
// integration delay.
func (s *LightningOutputsTestSuite) serverEnforcementOutputForTest(
	paymentHash []byte,
) (testHTLCParticipants, *addresses.ToServerHTLCEnforcementOutput) {
	s.T().Helper()

	keys := s.newTestHTLCParticipants()
	output, err := addresses.NewToServerHTLCEnforcementOutput(
		s.integrationKeyTriple(),
		integrationPath,
		keys.revocationPrivKey.PubKey(),
		paymentHash,
	)
	s.NoError(err)
	return keys, output
}
