package libwallet

import (
	"bytes"
	"encoding/hex"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/pkg/errors"
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

// TODO: Change name
type RedeemableAddress interface {
	RedeemScript() []byte
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

type Input interface {
	OutPoint() Outpoint
	Address() MuunAddress
	UserSignature() []byte
	MuunSignature() []byte
	SubmarineSwapV1() InputSubmarineSwapV1
	SubmarineSwapV2() InputSubmarineSwapV2
}

type PartiallySignedTransaction struct {
	tx           *wire.MsgTx
	inputs       []Input
	Expectations *SigningExpectations
}

type Transaction struct {
	Hash  string
	Bytes []byte
}

func NewPartiallySignedTransaction(hexTx string) (*PartiallySignedTransaction, error) {

	rawTx, err := hex.DecodeString(hexTx)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to decode hex tx")
	}

	tx := wire.NewMsgTx(0)
	err = tx.BtcDecode(bytes.NewBuffer(rawTx), 0, wire.WitnessEncoding)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to decode tx")
	}

	return &PartiallySignedTransaction{tx: tx, inputs: []Input{}}, nil
}

func (p *PartiallySignedTransaction) AddInput(input Input) {
	p.inputs = append(p.inputs, input)
}

func (p *PartiallySignedTransaction) Sign(key *HDPrivateKey, muunKey *HDPublicKey) (*Transaction, error) {

	for i, input := range p.inputs {

		derivedKey, err := key.DeriveTo(input.Address().DerivationPath())
		if err != nil {
			return nil, errors.Wrapf(err, "failed to derive user key")
		}

		derivedMuunKey, err := muunKey.DeriveTo(input.Address().DerivationPath())
		if err != nil {
			return nil, errors.Wrapf(err, "failed to derive muun key")
		}

		var txIn *wire.TxIn

		switch AddressVersion(input.Address().Version()) {
		case addressV1:
			txIn, err = addUserSignatureInputV1(input, i, p.tx, derivedKey)
		case addressV2:
			txIn, err = addUserSignatureInputV2(input, i, p.tx, derivedKey, derivedMuunKey)
		case addressV3:
			txIn, err = addUserSignatureInputV3(input, i, p.tx, derivedKey, derivedMuunKey)
		case addressV4:
			txIn, err = addUserSignatureInputV4(input, i, p.tx, derivedKey, derivedMuunKey)
		case addressSubmarineSwapV1:
			txIn, err = addUserSignatureInputSubmarineSwapV1(input, i, p.tx, derivedKey, derivedMuunKey)
		case addressSubmarineSwapV2:
			txIn, err = addUserSignatureInputSubmarineSwapV2(input, i, p.tx, derivedKey, derivedMuunKey)
		default:
			return nil, errors.Errorf("cant sign transaction of version %v", input.Address().Version())
		}

		if err != nil {
			return nil, errors.Wrapf(err, "failed to sign input using version %v", input.Address().Version())
		}

		p.tx.TxIn[i] = txIn
	}

	var writer bytes.Buffer
	err := p.tx.BtcEncode(&writer, 0, wire.WitnessEncoding)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to encode tx")
	}

	return &Transaction{
		Hash:  p.tx.TxHash().String(),
		Bytes: writer.Bytes(),
	}, nil

}

func (p *PartiallySignedTransaction) MuunSignatureForInput(index int, userKey *HDPublicKey,
	muunKey *HDPrivateKey) ([]byte, error) {

	input := p.inputs[index]

	derivedUserKey, err := userKey.DeriveTo(input.Address().DerivationPath())
	if err != nil {
		return nil, errors.Wrapf(err, "failed to derive user key")
	}

	derivedMuunKey, err := muunKey.DeriveTo(input.Address().DerivationPath())
	if err != nil {
		return nil, errors.Wrapf(err, "failed to derive muun key")
	}

	switch AddressVersion(input.Address().Version()) {
	case addressV1:
		return []byte{}, nil
	case addressV2:
		return signInputV2(input, index, p.tx, derivedUserKey, derivedMuunKey.PublicKey(), derivedMuunKey)
	case addressV3:
		return signInputV3(input, index, p.tx, derivedUserKey, derivedMuunKey.PublicKey(), derivedMuunKey)
	case addressV4:
		return signInputV4(input, index, p.tx, derivedUserKey, derivedMuunKey.PublicKey(), derivedMuunKey)
	case addressSubmarineSwapV1:
		return nil, errors.New("cant sign arbitrary submarine swap v1 inputs")
	case addressSubmarineSwapV2:
		return nil, errors.New("cant sign arbitrary submarine swap v2 inputs")
	}

	return nil, errors.New("unknown address scheme")
}

func (p *PartiallySignedTransaction) Verify(userPublicKey *HDPublicKey, muunPublickKey *HDPublicKey) error {

	// TODO: We don't have enough information (yet) to check the inputs are actually ours and they exist.

	network := userPublicKey.Network.network

	// We expect TX to be frugal in their ouputs: one to the destination and an optional change.
	// If we were to receive more than that, we consider it invalid.
	if p.Expectations.change != nil {
		if len(p.tx.TxOut) != 2 {
			return errors.Errorf("expected destination and change outputs but found %v", len(p.tx.TxOut))
		}
	} else {
		if len(p.tx.TxOut) != 1 {
			return errors.Errorf("expected destination output only but found %v", len(p.tx.TxOut))
		}
	}

	addressToScript := func(address string) ([]byte, error) {
		parsedAddress, err := btcutil.DecodeAddress(address, network)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to parse address %v", address)
		}
		script, err := txscript.PayToAddrScript(parsedAddress)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to generate script for address %v", address)
		}

		return script, nil
	}

	toScript, err := addressToScript(p.Expectations.destination)
	if err != nil {
		return err
	}

	expectedAmount := p.Expectations.amount
	expectedFee := p.Expectations.fee
	expectedChange := p.Expectations.change

	var changeScript []byte
	if expectedChange != nil {
		changeScript, err = addressToScript(p.Expectations.change.Address())
		if err != nil {
			return err
		}
	}

	var toOutput, changeOutput *wire.TxOut
	for _, output := range p.tx.TxOut {
		if bytes.Equal(output.PkScript, toScript) {
			toOutput = output
		} else if changeScript != nil && bytes.Equal(output.PkScript, changeScript) {
			changeOutput = output
		}
	}

	if toOutput == nil {
		return errors.Errorf("destination output is not present")
	}

	if toOutput.Value != expectedAmount {
		return errors.Errorf("destination amount is mismatched. found %v expected %v", toOutput.Value, expectedAmount)
	}

	/*
		NOT CHECKED: outputs smaller than DUST.
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

	var expectedChangeAmount int64
	if expectedChange != nil {
		if changeOutput == nil {
			return errors.Errorf("Change is not present")
		}

		expectedChangeAmount = actualTotal - expectedAmount - expectedFee
		if changeOutput.Value != expectedChangeAmount {
			return errors.Errorf("Change amount is mismatched. found %v expected %v",
				changeOutput.Value, expectedChangeAmount)
		}

		derivedUserKey, err := userPublicKey.DeriveTo(expectedChange.DerivationPath())
		if err != nil {
			return errors.Wrapf(err, "failed to derive user key to change path %v",
				expectedChange.DerivationPath())
		}

		derivedMuunKey, err := muunPublickKey.DeriveTo(expectedChange.DerivationPath())
		if err != nil {
			return errors.Wrapf(err, "failed to derive muun key to change path %v",
				expectedChange.DerivationPath())
		}

		expectedChangeAddress, err := newMuunAddress(AddressVersion(expectedChange.Version()), derivedUserKey, derivedMuunKey)
		if err != nil {
			return errors.Wrapf(err, "failed to build the change address with version %v",
				expectedChange.Version())
		}

		if expectedChangeAddress.Address() != expectedChange.Address() {
			return errors.Errorf("mismatched change address. found %v, expected %v",
				expectedChange.Address(), expectedChangeAddress.Address())
		}
	}

	actualFee := actualTotal - expectedAmount - expectedChangeAmount
	if actualFee != expectedFee {
		return errors.Errorf("fee mismatched. found %v, expected %v", actualFee, expectedFee)
	}

	/*
		NOT CHECKED: locktimes.
		Using locktimes set in the future would invalidate the transaction, so the crafter could prevent us from spending
		money. However, we would inflict the same denial on ourselves by rejecting it. Also, we'll eventually rely on
		locktimes ourselves and would then need version checks to decide whether to send them to specific clients.
	*/

	return nil
}
