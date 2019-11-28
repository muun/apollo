package libwallet

import (
	"bytes"
	"encoding/hex"

	"github.com/btcsuite/btcd/wire"
	"github.com/pkg/errors"
)

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
	tx     *wire.MsgTx
	inputs []Input
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
