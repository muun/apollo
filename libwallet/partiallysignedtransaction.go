package libwallet

import (
	"bytes"
	"encoding/hex"
	"errors"
	"fmt"

	"github.com/muun/libwallet/addresses"

	"github.com/btcsuite/btcd/chaincfg/chainhash"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
)

type SigningExpectations struct {
	destination string
	amount      int64
	change      MuunAddress
	fee         int64
}

func NewSigningExpectations(destination string, amount int64, change MuunAddress, fee int64) *SigningExpectations {
	return &SigningExpectations{
		destination,
		amount,
		change,
		fee,
	}
}

type MuunAddress interface {
	Version() int
	DerivationPath() string
	Address() string
}

type Outpoint interface {
	TxId() []byte
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
	MuunPublicKey() []byte
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
}

type Input interface {
	OutPoint() Outpoint
	Address() MuunAddress
	UserSignature() []byte
	MuunSignature() []byte
	SubmarineSwapV1() InputSubmarineSwapV1
	SubmarineSwapV2() InputSubmarineSwapV2
	IncomingSwap() InputIncomingSwap
}

type PartiallySignedTransaction struct {
	tx     *wire.MsgTx
	inputs []Input
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

func NewPartiallySignedTransaction(inputs *InputList, rawTx []byte) (*PartiallySignedTransaction, error) {

	tx := wire.NewMsgTx(0)
	err := tx.Deserialize(bytes.NewReader(rawTx))
	if err != nil {
		return nil, fmt.Errorf("failed to decode tx: %w", err)
	}

	return &PartiallySignedTransaction{tx: tx, inputs: inputs.Inputs()}, nil
}

func (p *PartiallySignedTransaction) coins(net *Network) ([]coin, error) {
	var coins []coin
	for _, input := range p.inputs {
		coin, err := createCoin(input, net)
		if err != nil {
			return nil, err
		}
		coins = append(coins, coin)
	}
	return coins, nil
}

func (p *PartiallySignedTransaction) Sign(userKey *HDPrivateKey, muunKey *HDPublicKey) (*Transaction, error) {

	coins, err := p.coins(userKey.Network)
	if err != nil {
		return nil, fmt.Errorf("could not convert input data to coin: %w", err)
	}

	for i, coin := range coins {
		err = coin.SignInput(i, p.tx, userKey, muunKey)
		if err != nil {
			return nil, fmt.Errorf("failed to sign input: %w", err)
		}
	}

	return newTransaction(p.tx)

}

func (p *PartiallySignedTransaction) FullySign(userKey, muunKey *HDPrivateKey) (*Transaction, error) {

	coins, err := p.coins(userKey.Network)
	if err != nil {
		return nil, fmt.Errorf("could not convert input data to coin: %w", err)
	}

	for i, coin := range coins {
		err = coin.FullySignInput(i, p.tx, userKey, muunKey)
		if err != nil {
			return nil, fmt.Errorf("failed to sign input: %w", err)
		}
	}

	return newTransaction(p.tx)
}

func (p *PartiallySignedTransaction) Verify(expectations *SigningExpectations, userPublicKey *HDPublicKey, muunPublickKey *HDPublicKey) error {

	// TODO: We don't have enough information (yet) to check the inputs are actually ours and they exist.

	network := userPublicKey.Network

	// We expect TX to be frugal in their ouputs: one to the destination and an optional change.
	// If we were to receive more than that, we consider it invalid.
	if expectations.change != nil {
		if len(p.tx.TxOut) != 2 {
			return fmt.Errorf("expected destination and change outputs but found %v", len(p.tx.TxOut))
		}
	} else {
		if len(p.tx.TxOut) != 1 {
			return fmt.Errorf("expected destination output only but found %v", len(p.tx.TxOut))
		}
	}

	// Build output script corresponding to the destination address.
	toScript, err := addressToScript(expectations.destination, network)
	if err != nil {
		return err
	}

	expectedAmount := expectations.amount
	expectedFee := expectations.fee
	expectedChange := expectations.change

	// Build output script corresponding to the change address.
	var changeScript []byte
	if expectedChange != nil {
		changeScript, err = addressToScript(expectations.change.Address(), network)
		if err != nil {
			return err
		}
	}

	// Find destination and change outputs using the script we just built.
	var toOutput, changeOutput *wire.TxOut
	for _, output := range p.tx.TxOut {
		if bytes.Equal(output.PkScript, toScript) {
			toOutput = output
		} else if changeScript != nil && bytes.Equal(output.PkScript, changeScript) {
			changeOutput = output
		}
	}

	// Fail if not destination output was found in the TX.
	if toOutput == nil {
		return errors.New("destination output is not present")
	}

	// Verify destination output value matches expected amount
	if toOutput.Value != expectedAmount {
		return fmt.Errorf("destination amount is mismatched. found %v expected %v", toOutput.Value, expectedAmount)
	}

	/*
		NOT CHECKED: outputs smaller than dustThreshold.
		We removed this check, which could be exploited by the crafter to invalidate the transaction. Since failing the
		integrity check ourselves would have the same effect (preventing us from signing) it doesn't make much sense.
	*/

	var actualTotal int64
	for _, input := range p.inputs {
		actualTotal += input.OutPoint().Amount()
	}

	/*
		NOT CHECKED: input amounts.
		These are provided by the crafter, but for segwit inputs (scheme v3 and forward), the amount is part of
		the data to sign. Thus, they can't be manipulated without invalidating the signature.
		Client's using this code are all generating v3 or superior addresses. They could still have older UTXOs, but
		they should be rare, only a handful of users ever used v1 and v2 addresses.
	*/

	// Verify change output is spendable by the wallet.
	if expectedChange != nil {
		if changeOutput == nil {
			return errors.New("change is not present")
		}

		expectedChangeAmount := actualTotal - expectedAmount - expectedFee
		if changeOutput.Value != expectedChangeAmount {
			return fmt.Errorf("change amount is mismatched. found %v expected %v",
				changeOutput.Value, expectedChangeAmount)
		}

		derivedUserKey, err := userPublicKey.DeriveTo(expectedChange.DerivationPath())
		if err != nil {
			return fmt.Errorf("failed to derive user key to change path %v: %w",
				expectedChange.DerivationPath(), err)
		}

		derivedMuunKey, err := muunPublickKey.DeriveTo(expectedChange.DerivationPath())
		if err != nil {
			return fmt.Errorf("failed to derive muun key to change path %v: %w",
				expectedChange.DerivationPath(), err)
		}

		expectedChangeAddress, err := addresses.Create(
			expectedChange.Version(),
			&derivedUserKey.key,
			&derivedMuunKey.key,
			expectedChange.DerivationPath(),
			network.network,
		)
		if err != nil {
			return fmt.Errorf("failed to build the change address with version %v: %w",
				expectedChange.Version(), err)
		}

		if expectedChangeAddress.Address() != expectedChange.Address() {
			return fmt.Errorf("mismatched change address. found %v, expected %v",
				expectedChange.Address(), expectedChangeAddress.Address())
		}

		actualFee := actualTotal - expectedAmount - expectedChangeAmount
		if actualFee != expectedFee {
			return fmt.Errorf("fee mismatched. found %v, expected %v", actualFee, expectedFee)
		}

	} else {
		actualFee := actualTotal - expectedAmount
		if actualFee >= expectedFee+dustThreshold {
			return errors.New("change output is too big to be burned as fee")
		}
	}

	/*
		NOT CHECKED: locktimes.
		Using locktimes set in the future would invalidate the transaction, so the crafter could prevent us from spending
		money. However, we would inflict the same denial on ourselves by rejecting it. Also, we'll eventually rely on
		locktimes ourselves and would then need version checks to decide whether to send them to specific clients.
	*/

	return nil
}

func addressToScript(address string, network *Network) ([]byte, error) {
	parsedAddress, err := btcutil.DecodeAddress(address, network.network)
	if err != nil {
		return nil, fmt.Errorf("failed to parse address %v: %w", address, err)
	}
	script, err := txscript.PayToAddrScript(parsedAddress)
	if err != nil {
		return nil, fmt.Errorf("failed to generate script for address %v: %w", address, err)
	}
	return script, nil
}

func newTransaction(tx *wire.MsgTx) (*Transaction, error) {
	var buf bytes.Buffer
	err := tx.Serialize(&buf)
	if err != nil {
		return nil, fmt.Errorf("failed to encode tx: %w", err)
	}

	return &Transaction{
		Hash:  tx.TxHash().String(),
		Bytes: buf.Bytes(),
	}, nil
}

type coin interface {
	// TODO: these two methods can be collapsed into a single one once we move
	// it to a submodule and use *hdkeychain.ExtendedKey's for the arguments.
	SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error
	FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error
}

func createCoin(input Input, network *Network) (coin, error) {
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

	switch version {
	case addresses.V1:
		return &coinV1{
			Network:  network.network,
			OutPoint: outPoint,
			KeyPath:  keyPath,
		}, nil
	case addresses.V2:
		return &coinV2{
			Network:       network.network,
			OutPoint:      outPoint,
			KeyPath:       keyPath,
			MuunSignature: input.MuunSignature(),
		}, nil
	case addresses.V3:
		return &coinV3{
			Network:       network.network,
			OutPoint:      outPoint,
			KeyPath:       keyPath,
			Amount:        amount,
			MuunSignature: input.MuunSignature(),
		}, nil
	case addresses.V4:
		return &coinV4{
			Network:       network.network,
			OutPoint:      outPoint,
			KeyPath:       keyPath,
			Amount:        amount,
			MuunSignature: input.MuunSignature(),
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
			MuunPublicKey:       swap.MuunPublicKey(),
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
			MuunSignature:       input.MuunSignature(),
			Sphinx:              swap.Sphinx(),
			HtlcTx:              swap.HtlcTx(),
			PaymentHash256:      swap.PaymentHash256(),
			SwapServerPublicKey: swapServerPublicKey,
			ExpirationHeight:    swap.ExpirationHeight(),
			Collect:             btcutil.Amount(swap.CollectInSats()),
		}, nil
	default:
		return nil, fmt.Errorf("can't create coin from input version %v", version)
	}
}
