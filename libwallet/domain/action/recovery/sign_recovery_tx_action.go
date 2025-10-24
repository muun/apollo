package recovery

import (
	"bytes"
	"fmt"
	"github.com/btcsuite/btcd/wire"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/scanner"
)

type SignSweepTxAction struct {
	keyProvider keys.KeyProvider
	network     *libwallet.Network
}

func NewSignSweepTxAction(keyProvider keys.KeyProvider, network *libwallet.Network) *SignSweepTxAction {
	return &SignSweepTxAction{
		keyProvider: keyProvider,
		network:     network,
	}
}

func (action *SignSweepTxAction) Run(utxos []*scanner.Utxo, tx *wire.MsgTx, recoveryCode string) (*wire.MsgTx, error) {
	userPrivateKey, err := action.keyProvider.UserPrivateKey()
	if err != nil {
		return nil, err
	}

	muunPrivateKey, err := action.fetchMuunPrivateKey(recoveryCode)
	if err != nil {
		return nil, err
	}

	return buildSignedSweepTx(utxos, tx, userPrivateKey, muunPrivateKey)
}

func (action *SignSweepTxAction) fetchMuunPrivateKey(recoveryCode string) (*libwallet.HDPrivateKey, error) {
	encryptedKeyData, err := action.keyProvider.EncryptedMuunPrivateKey()
	if err != nil {
		return nil, err
	}

	muunKeyData, err := decryptKeys(encryptedKeyData, recoveryCode, action.network)
	if err != nil {
		return nil, err
	}

	return muunKeyData.Key, nil
}

func decryptKeys(encryptedKey *libwallet.EncryptedPrivateKeyInfo, recoveryCode string, network *libwallet.Network) (*libwallet.DecryptedPrivateKey, error) {
	decryptionKey, err := libwallet.RecoveryCodeToKey(recoveryCode, encryptedKey.Salt)

	if err != nil {
		return nil, fmt.Errorf("failed to process recovery code: %w", err)
	}

	decryptedKey, err := decryptionKey.DecryptKey(encryptedKey, network)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt key: %w", err)
	}

	return decryptedKey, nil
}

func buildSignedSweepTx(utxos []*scanner.Utxo, unsignedSweepTx *wire.MsgTx, userKey *libwallet.HDPrivateKey, muunKey *libwallet.HDPrivateKey) (*wire.MsgTx, error) {
	inputList := &libwallet.InputList{}
	userNonces := libwallet.EmptyMusigNonces()

	for _, utxo := range utxos {
		inputList.Add(&input{
			utxo,
			[]byte{},
		})

		// generate the user nonce for the user signing key
		key, err := userKey.DeriveTo(utxo.Address.DerivationPath())
		if err != nil {
			return nil, err
		}
		_, err = userNonces.GenerateNonce(utxo.Address.Version(), key.PublicKey().Raw())
		if err != nil {
			return nil, err
		}
	}

	writer := &bytes.Buffer{}
	err := unsignedSweepTx.Serialize(writer)
	if err != nil {
		return nil, err
	}

	sweepTxBytes := writer.Bytes()

	pstx, err := libwallet.NewPartiallySignedTransaction(inputList, sweepTxBytes, userNonces)
	if err != nil {
		return nil, err
	}

	signedTx, err := pstx.FullySign(userKey, muunKey)
	if err != nil {
		return nil, err
	}

	wireTx := wire.NewMsgTx(0)
	err = wireTx.BtcDecode(bytes.NewReader(signedTx.Bytes), 0, wire.WitnessEncoding)
	if err != nil {
		return nil, err
	}

	return wireTx, nil
}
