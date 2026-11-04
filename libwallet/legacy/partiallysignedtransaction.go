package libwallet

import (
	"bytes"
	"encoding/hex"
	"strings"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/legacy/addresses"
	"github.com/muun/libwallet/legacy/btcsuitew/btcutilw"
	"github.com/muun/libwallet/legacy/btcsuitew/txscriptw"
	"github.com/muun/libwallet/platform/secret"
)

type SigningExpectations struct {
	destination       string
	amount            int64
	change            MuunAddress
	fee               int64
	alternative       bool
	expectedDebtInSat int64
}

func NewSigningExpectations(
	destination string,
	amount int64,
	change MuunAddress,
	fee int64,
	alternative bool,
	expectedDebtInSat int64,
) *SigningExpectations {
	return &SigningExpectations{
		destination:       destination,
		amount:            amount,
		change:            change,
		fee:               fee,
		alternative:       alternative,
		expectedDebtInSat: expectedDebtInSat,
	}
}

func (e *SigningExpectations) ForAlternativeTransaction() *SigningExpectations {
	return &SigningExpectations{
		destination:       e.destination,
		amount:            e.amount,
		change:            e.change,
		fee:               e.fee,
		alternative:       true,
		expectedDebtInSat: e.expectedDebtInSat,
	}
}

type MuunAddress interface {
	Version() int
	DerivationPath() string
	Address() string
}

type Outpoint interface {
	TxId() []byte //nolint:staticcheck // should be TxID, but it's part of the gomobile contract with the apps
	Index() int
	Amount() int64
}

type InputSubmarineSwapV1 interface {
	RefundAddress() string
	PaymentHash256() []byte
	ServerPublicKey() []byte
	LockTime() int64
}

type InputSubmarineSwapV2 interface {
	PaymentHash256() []byte
	UserPublicKey() []byte
	CosignerPublicKey() []byte
	ServerPublicKey() []byte
	BlocksForExpiration() int64
	ServerSignature() []byte
}

type InputIncomingSwap interface {
	Sphinx() []byte
	HtlcTx() []byte
	PaymentHash256() []byte
	SwapServerPublicKey() string
	ExpirationHeight() int64
	CollectInSats() int64
	Preimage() []byte
	HtlcOutputKeyPath() string
}

type Input interface {
	OutPoint() Outpoint
	Address() MuunAddress
	UserSignature() []byte
	CosignerSignature() []byte
	SubmarineSwapV1() InputSubmarineSwapV1
	SubmarineSwapV2() InputSubmarineSwapV2
	IncomingSwap() InputIncomingSwap
	CosignerPublicNonce() []byte
}

type PartiallySignedTransaction struct {
	tx     *wire.MsgTx
	inputs []Input

	// UserNonces
	nonces *MusigNonces
}

type Transaction struct {
	Hash  string
	Bytes []byte
}

const dustThreshold = 546

type InputList struct {
	inputs []Input
}

func (l *InputList) Add(input Input) {
	l.inputs = append(l.inputs, input)
}

func (l *InputList) Inputs() []Input {
	return l.inputs
}

func NewPartiallySignedTransaction(
	inputs *InputList, rawTx []byte, userNonces *MusigNonces,
) (*PartiallySignedTransaction, error) {

	tx := wire.NewMsgTx(0)
	err := tx.Deserialize(bytes.NewReader(rawTx))
	if err != nil {
		return nil, errors.Errorf("failed to decode tx: %w", err)
	}

	return &PartiallySignedTransaction{
		tx:     tx,
		inputs: inputs.Inputs(),
		nonces: userNonces,
	}, nil
}

func (p *PartiallySignedTransaction) coins(net *Network) ([]coin, error) {
	var coins []coin

	prevOuts, err := p.createPrevOuts(net)
	if err != nil {
		return nil, err
	}

	// TODO:
	// Only taproot coins are going to use this kind of cache. SegWit v0 coins should use it too.
	sigHashes := txscriptw.NewTaprootSigHashes(p.tx, prevOuts)

	for i, input := range p.inputs {
		coin, err := createCoin(i, input, net, sigHashes, p.nonces)
		if err != nil {
			return nil, err
		}
		coins = append(coins, coin)
	}
	return coins, nil
}

func (p *PartiallySignedTransaction) createPrevOuts(net *Network) ([]*wire.TxOut, error) {
	prevOuts := make([]*wire.TxOut, len(p.inputs))

	for i, input := range p.inputs {
		amount := input.OutPoint().Amount()
		addr := input.Address().Address()

		decodedAddr, err := btcutilw.DecodeAddress(addr, net.network)
		if err != nil {
			return nil, errors.Errorf("failed to decode address %s in prevOut %d: %w", addr, i, err)
		}

		script, err := txscriptw.PayToAddrScript(decodedAddr)
		if err != nil {
			return nil, errors.Errorf(
				"failed to craft output script for %s in prevOut %d: %w",
				addr,
				i,
				err,
			)
		}

		prevOuts[i] = &wire.TxOut{Value: amount, PkScript: script}
	}

	return prevOuts, nil
}

func (p *PartiallySignedTransaction) Sign(
	userKey *HDPrivateKey,
	cosignerKey *HDPublicKey,
) (*Transaction, error) {

	coins, err := p.coins(userKey.Network)
	if err != nil {
		return nil, errors.Errorf("could not convert input data to coin: %w", err)
	}

	for i, coin := range coins {
		err = coin.SignInput(i, p.tx, userKey, cosignerKey)
		if err != nil {
			return nil, errors.Errorf("failed to sign input: %w", err)
		}
	}

	return newTransaction(p.tx)

}

func (p *PartiallySignedTransaction) FullySign(
	userKey, cosignerKey *HDPrivateKey,
) (*Transaction, error) {

	coins, err := p.coins(userKey.Network)
	if err != nil {
		return nil, errors.Errorf("could not convert input data to coin: %w", err)
	}

	for i, coin := range coins {
		err = coin.FullySignInput(i, p.tx, userKey, cosignerKey)
		if err != nil {
			return nil, errors.Errorf("failed to sign input: %w", err)
		}
	}

	return newTransaction(p.tx)
}

// VerificationResult is the outcome of checking a partially signed transaction against the
// expectations the user approved.
//
// Checks come in two groups. Enforced checks gate signing: if one fails, the crafter is trying to
// get us to sign something the user did not approve. Incubating checks don't stop us from signing
// yet; they only feed telemetry.
type VerificationResult struct {
	// Enforced.
	destination error
	ownership   error
	evaluation  error
	outputShape error

	// Incubating.
	changeAmount error
	fee          error
}

// MustNotSign reports whether an enforced check failed. Callers must not sign when it's true.
func (r *VerificationResult) MustNotSign() bool {
	return r.destination != nil || r.ownership != nil || r.evaluation != nil ||
		r.outputShape != nil
}

// IncubatingCheckFailed reports whether a check we don't enforce yet failed. Callers log these so
// we can learn whether they're safe to enforce.
func (r *VerificationResult) IncubatingCheckFailed() bool {
	return r.changeAmount != nil || r.fee != nil
}

// FailedChecks names the checks that failed, comma separated. It carries no amounts, so reports can
// be grouped and filtered by it. Empty when the transaction verified cleanly.
func (r *VerificationResult) FailedChecks() string {
	var names []string

	for _, check := range []struct {
		name string
		err  error
	}{
		{"destination", r.destination},
		{"ownership", r.ownership},
		{"evaluation", r.evaluation},
		{"outputShape", r.outputShape},
		{"changeAmount", r.changeAmount},
		{"fee", r.fee},
	} {
		if check.err != nil {
			names = append(names, check.name)
		}
	}

	return strings.Join(names, ",")
}

// Summary describes every check that failed. Empty when the transaction verified cleanly.
func (r *VerificationResult) Summary() string {
	var failures []string

	for _, err := range []error{
		r.destination,
		r.ownership,
		r.evaluation,
		r.outputShape,
		r.changeAmount,
		r.fee,
	} {
		if err != nil {
			failures = append(failures, err.Error())
		}
	}

	return strings.Join(failures, "; ")
}

func (p *PartiallySignedTransaction) Verify(
	expectations *SigningExpectations,
	userPublicKey *HDPublicKey,
	cosignerPublicKey *HDPublicKey,
) *VerificationResult {

	result := &VerificationResult{}

	// TODO: We don't have enough information (yet) to check the inputs are actually ours and they
	// exist.

	network := userPublicKey.Network

	expectedAmount := expectations.amount
	expectedFee := expectations.fee
	expectedChange := expectations.change

	// Build output script corresponding to the destination address.
	toScript, err := addressToScript(expectations.destination, network)
	if err != nil {
		result.evaluation = err
		return result
	}

	// Build output script corresponding to the change address.
	var changeScript []byte
	if expectedChange != nil {
		changeScript, err = addressToScript(expectedChange.Address(), network)
		if err != nil {
			result.evaluation = err
			return result
		}
	}

	// Find destination and change outputs using the scripts we just built. Anything else pays
	// someone who is neither the destination nor us.
	var toOutput, changeOutput *wire.TxOut
	var foreignOutputs int
	for _, output := range p.tx.TxOut {
		if bytes.Equal(output.PkScript, toScript) {
			toOutput = output

		} else if changeScript != nil && bytes.Equal(output.PkScript, changeScript) {
			changeOutput = output

		} else {
			foreignOutputs++
		}
	}

	if foreignOutputs > 0 {
		result.ownership = errors.Errorf(
			"found %v output(s) paying neither the destination nor our change", foreignOutputs)
	}

	if result.ownership == nil && expectedChange != nil {
		result.ownership = verifyChangeAddress(
			expectedChange,
			userPublicKey,
			cosignerPublicKey,
			network,
		)
	}

	result.destination = verifyDestinationOutput(expectations, toOutput, changeOutput)

	// The shape doesn't depend on the amounts, so we check it even when the destination is wrong:
	// it's what tells a wrong address apart from extra outputs smuggled in.
	result.outputShape = verifyOutputShape(toOutput, changeOutput, len(p.tx.TxOut))

	// The change and fee checks are relative to the destination amount, so they say nothing once
	// the destination itself is wrong. MustNotSign already covers that case.
	if result.destination != nil {
		return result
	}

	// Alternative TXs pay the destination less than the user approved, moving the difference to
	// fee, so re-adjust before checking amounts.
	if expectations.alternative {
		if toOutput == nil {
			expectedFee += expectedAmount
			expectedAmount = 0
		} else {
			expectedFee += expectedAmount - toOutput.Value
			expectedAmount = toOutput.Value
		}
	}

	/*
		NOT CHECKED: outputs smaller than dustThreshold.
		We removed this check, which could be exploited by the crafter to
		invalidate the transaction. Since failing the integrity check
		ourselves would have the same effect (preventing us from signing)
		it doesn't make much sense.
	*/

	var actualTotal int64
	for _, input := range p.inputs {
		actualTotal += input.OutPoint().Amount()
	}

	/*
		NOT CHECKED: input amounts.
		These are provided by the crafter, but for segwit inputs
		(scheme v3 and forward), the amount is part of the data to sign.
		Thus, they can't be manipulated without invalidating the
		signature. Client's using this code are all generating v3 or
		superior addresses. They could still have older UTXOs, but they
		should be rare, only a handful of users ever used v1 and v2
		addresses.
	*/

	expectedDebt := expectations.expectedDebtInSat

	if expectedChange != nil {
		// Checking the change amount also checks the fee: the two add up to the inputs minus the
		// destination amount, so one can't be wrong on its own.
		expectedChangeAmount := actualTotal - expectedAmount - expectedFee

		if changeOutput == nil {
			if expectedChangeAmount >= expectedDebt+dustThreshold {
				result.changeAmount = errors.Errorf(
					"change of %v is not present, which the debt of %v does not account for",
					expectedChangeAmount, expectedDebt)
			}

		} else {
			shortfall := expectedChangeAmount - changeOutput.Value

			if shortfall < 0 || shortfall > expectedDebt {
				result.changeAmount = errors.Errorf(
					"change amount is mismatched. found %v expected %v, which the debt of %v "+
						"does not account for",
					changeOutput.Value, expectedChangeAmount, expectedDebt)
			}
		}

	} else {
		actualFee := actualTotal - expectedAmount
		if actualFee >= expectedFee+expectedDebt+dustThreshold {
			result.fee = errors.Errorf(
				"change output is too big to be burned as fee. actual fee: %v, expected: %v, "+
					"debt: %v",
				actualFee, expectedFee, expectedDebt,
			)
		}
	}

	/*
		NOT CHECKED: locktimes.
		Using locktimes set in the future would invalidate the
		transaction, so the crafter could prevent us from spending money.
		However, we would inflict the same denial on ourselves by
		rejecting it. Also, we'll eventually rely on locktimes ourselves
		and would then need version checks to decide whether to send
		them to specific clients.
	*/

	return result
}

// verifyChangeAddress checks that the change address is one the wallet can spend, which we establish
// by re-deriving it from our own keys.
func verifyChangeAddress(
	expectedChange MuunAddress,
	userPublicKey *HDPublicKey,
	cosignerPublicKey *HDPublicKey,
	network *Network,
) error {

	derivedUserKey, err := userPublicKey.DeriveTo(expectedChange.DerivationPath())
	if err != nil {
		return errors.Errorf("failed to derive user key to change path %v: %w",
			expectedChange.DerivationPath(), err)
	}

	derivedCosignerKey, err := cosignerPublicKey.DeriveTo(expectedChange.DerivationPath())
	if err != nil {
		return errors.Errorf("failed to derive cosigner key to change path %v: %w",
			expectedChange.DerivationPath(), err)
	}

	expectedChangeAddress, err := addresses.Create(
		expectedChange.Version(),
		&derivedUserKey.key,
		&derivedCosignerKey.key,
		expectedChange.DerivationPath(),
		network.network,
	)
	if err != nil {
		return errors.Errorf("failed to build the change address with version %v: %w",
			expectedChange.Version(), err)
	}

	if expectedChangeAddress.Address() != expectedChange.Address() {
		return errors.Errorf("mismatched change address. found %v, expected %v",
			expectedChange.Address(), expectedChangeAddress.Address())
	}

	return nil
}

// verifyDestinationOutput checks that the destination is paid what the user approved.
func verifyDestinationOutput(
	expectations *SigningExpectations,
	toOutput, changeOutput *wire.TxOut,
) error {

	if !expectations.alternative {
		if toOutput == nil {
			return errors.New("destination output is not present")
		}

		if toOutput.Value != expectations.amount {
			return errors.Errorf(
				"destination amount is mismatched. found %v expected %v",
				toOutput.Value,
				expectations.amount,
			)
		}

		return nil
	}

	// Alternative TXs pay the destination less than approved, moving the difference to fee, and
	// might not pay it at all when there's a change output to reduce instead.
	if toOutput == nil && changeOutput == nil {
		return errors.New("expected at least one of destination and change outputs but found zero")
	}

	if toOutput == nil {
		return nil
	}

	// Paying the destination less than approved is the whole point of an alternative TX.
	if toOutput.Value < expectations.amount {
		return nil
	}

	// A destination at the dust floor can't be reduced any further, so an alternative has no choice
	// but to pay exactly the approved amount. Paying the destination what the user approved is
	// never theft, so allow it there.
	if toOutput.Value == expectations.amount && expectations.amount <= dustThreshold {
		return nil
	}

	return errors.Errorf(
		"destination amount is mismatched. found %v expected at most %v",
		toOutput.Value,
		expectations.amount,
	)
}

// verifyOutputShape checks that the TX pays nothing but the destination and our own change: at most
// two outputs, and when there are two, one of each.
func verifyOutputShape(toOutput, changeOutput *wire.TxOut, outputCount int) error {

	if outputCount > 2 {
		return errors.Errorf(
			"expected at most destination and change outputs but found %v", outputCount)
	}

	if outputCount == 2 && (toOutput == nil || changeOutput == nil) {
		return errors.New("expected two outputs to be the destination and our change")
	}

	return nil
}

func addressToScript(address string, network *Network) ([]byte, error) {
	parsedAddress, err := btcutilw.DecodeAddress(address, network.network)
	if err != nil {
		return nil, errors.Errorf("failed to parse address %v: %w", address, err)
	}
	script, err := txscriptw.PayToAddrScript(parsedAddress)
	if err != nil {
		return nil, errors.Errorf("failed to generate script for address %v: %w", address, err)
	}
	return script, nil
}

func newTransaction(tx *wire.MsgTx) (*Transaction, error) {
	var buf bytes.Buffer
	err := tx.Serialize(&buf)
	if err != nil {
		return nil, errors.Errorf("failed to encode tx: %w", err)
	}

	return &Transaction{
		Hash:  tx.TxHash().String(),
		Bytes: buf.Bytes(),
	}, nil
}

type coin interface {
	// TODO: these two methods can be collapsed into a single one once we move
	// it to a submodule and use *hdkeychain.ExtendedKey's for the arguments.
	SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, cosignerKey *HDPublicKey) error
	FullySignInput(index int, tx *wire.MsgTx, userKey, cosignerKey *HDPrivateKey) error
}

func createCoin(
	index int,
	input Input,
	network *Network,
	sigHashes *txscriptw.TaprootSigHashes,
	userNonces *MusigNonces,
) (coin, error) {
	txID, err := chainhash.NewHash(input.OutPoint().TxId())
	if err != nil {
		return nil, err
	}
	outPoint := wire.OutPoint{
		Hash:  *txID,
		Index: uint32(input.OutPoint().Index()),
	}
	keyPath := input.Address().DerivationPath()
	amount := btcutil.Amount(input.OutPoint().Amount())

	version := input.Address().Version()

	if userNonces == nil {
		return nil, errors.Errorf("userNonces cannot be nil")
	}
	if len(userNonces.sessionIDs) <= index {
		return nil, errors.Errorf("not enough nonces were provided")
	}

	switch version {
	case addresses.V1:
		return &coinV1{
			Network:  network.network,
			OutPoint: outPoint,
			KeyPath:  keyPath,
		}, nil
	case addresses.V2:
		return &coinV2{
			Network:           network.network,
			OutPoint:          outPoint,
			KeyPath:           keyPath,
			CosignerSignature: input.CosignerSignature(),
		}, nil
	case addresses.V3:
		return &coinV3{
			Network:           network.network,
			OutPoint:          outPoint,
			KeyPath:           keyPath,
			Amount:            amount,
			CosignerSignature: input.CosignerSignature(),
		}, nil
	case addresses.V4:
		return &coinV4{
			Network:           network.network,
			OutPoint:          outPoint,
			KeyPath:           keyPath,
			Amount:            amount,
			CosignerSignature: input.CosignerSignature(),
		}, nil
	case addresses.V5:
		var nonce [66]byte
		copy(nonce[:], input.CosignerPublicNonce())
		var cosignerPartialSig [32]byte
		copy(cosignerPartialSig[:], input.CosignerSignature())
		return &coinV5{
			Network:            network.network,
			OutPoint:           outPoint,
			KeyPath:            keyPath,
			Amount:             amount,
			UserSessionID:      userNonces.sessionIDs[index],
			CosignerPubNonce:   nonce,
			CosignerPartialSig: cosignerPartialSig,
			SigHashes:          sigHashes,
		}, nil
	case addresses.V6:
		var nonce [66]byte
		copy(nonce[:], input.CosignerPublicNonce())
		var cosignerPartialSig [32]byte
		copy(cosignerPartialSig[:], input.CosignerSignature())
		return &coinV6{
			Network:            network.network,
			OutPoint:           outPoint,
			KeyPath:            keyPath,
			Amount:             amount,
			UserSessionID:      userNonces.sessionIDs[index],
			CosignerPubNonce:   nonce,
			CosignerPartialSig: cosignerPartialSig,
			SigHashes:          sigHashes,
		}, nil
	case addresses.SubmarineSwapV1:
		swap := input.SubmarineSwapV1()
		if swap == nil {
			return nil, errors.New("submarine swap data is nil for swap input")
		}
		return &coinSubmarineSwapV1{
			Network:         network.network,
			OutPoint:        outPoint,
			KeyPath:         keyPath,
			Amount:          amount,
			RefundAddress:   swap.RefundAddress(),
			PaymentHash256:  swap.PaymentHash256(),
			ServerPublicKey: swap.ServerPublicKey(),
			LockTime:        swap.LockTime(),
		}, nil
	case addresses.SubmarineSwapV2:
		swap := input.SubmarineSwapV2()
		if swap == nil {
			return nil, errors.New("submarine swap data is nil for swap input")
		}
		return &coinSubmarineSwapV2{
			Network:             network.network,
			OutPoint:            outPoint,
			KeyPath:             keyPath,
			Amount:              amount,
			PaymentHash256:      swap.PaymentHash256(),
			UserPublicKey:       swap.UserPublicKey(),
			CosignerPublicKey:   swap.CosignerPublicKey(),
			ServerPublicKey:     swap.ServerPublicKey(),
			BlocksForExpiration: swap.BlocksForExpiration(),
			ServerSignature:     swap.ServerSignature(),
		}, nil
	case addresses.IncomingSwap:
		swap := input.IncomingSwap()
		if swap == nil {
			return nil, errors.New("incoming swap data is nil for incoming swap input")
		}
		swapServerPublicKey, err := hex.DecodeString(swap.SwapServerPublicKey())
		if err != nil {
			return nil, err
		}
		return &coinIncomingSwap{
			Network:             network.network,
			CosignerSignature:   input.CosignerSignature(),
			Sphinx:              swap.Sphinx(),
			HtlcTx:              swap.HtlcTx(),
			PaymentHash256:      swap.PaymentHash256(),
			SwapServerPublicKey: swapServerPublicKey,
			ExpirationHeight:    swap.ExpirationHeight(),
			Collect:             btcutil.Amount(swap.CollectInSats()),
			Preimage:            secret.NewBytes(swap.Preimage()),
			HtlcOutputKeyPath:   swap.HtlcOutputKeyPath(),
		}, nil
	default:
		return nil, errors.Errorf("can't create coin from input version %v", version)
	}
}
