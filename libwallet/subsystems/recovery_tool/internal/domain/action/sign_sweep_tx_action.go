package action

import (
	"bytes"

	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/scanner"
)

type SignSweepTxAction interface {
	Run(
		utxos []*scanner.Utxo,
		tx *wire.MsgTx,
		recoveryCode string,
	) (*wire.MsgTx, error)
}

type signSweepTxAction struct {
	keyProvider adapted.KeyProvider
	network     *libwallet.Network
}

func NewSignSweepTxAction(
	keyProvider adapted.KeyProvider,
	network *libwallet.Network,
) SignSweepTxAction {
	return &signSweepTxAction{
		keyProvider: keyProvider,
		network:     network,
	}
}

func (action *signSweepTxAction) Run(
	utxos []*scanner.Utxo,
	tx *wire.MsgTx,
	recoveryCode string,
) (*wire.MsgTx, error) {
	cosignerPrivateKey, err := action.fetchCosignerPrivateKey(recoveryCode)
	if err != nil {
		return nil, err
	}

	var signedTx *wire.MsgTx
	err = action.keyProvider.WithUserPrivateKey(func(userPrivateKey *hd_keys.HDPrivateKey) error {
		facadeUserPrivateKey := libwallet.NewHDPrivateKeyFromModel(userPrivateKey)
		var signErr error
		signedTx, signErr = buildSignedSweepTx(utxos, tx, facadeUserPrivateKey, cosignerPrivateKey)
		return signErr
	})
	if err != nil {
		return nil, err
	}

	return signedTx, nil
}

// TODO: zero the decrypted cosigner key and the key derived from the recovery
// code to decrypt it.
func (action *signSweepTxAction) fetchCosignerPrivateKey(
	recoveryCode string,
) (*libwallet.HDPrivateKey, error) {
	encryptedKeyData, err := action.keyProvider.EncryptedCosignerPrivateKey()
	if err != nil {
		return nil, err
	}

	cosignerKeyData, err := decryptKeys(encryptedKeyData, recoveryCode, action.network)
	if err != nil {
		return nil, err
	}

	return cosignerKeyData.Key, nil
}

func decryptKeys(
	encryptedKey *libwallet.EncryptedPrivateKeyInfo,
	recoveryCode string,
	network *libwallet.Network,
) (*libwallet.DecryptedPrivateKey, error) {
	decryptionKey, err := libwallet.RecoveryCodeToKey(recoveryCode, encryptedKey.Salt)

	if err != nil {
		return nil, errors.Errorf("failed to process recovery code: %w", err)
	}

	decryptedKey, err := decryptionKey.DecryptKey(encryptedKey, network)
	if err != nil {
		return nil, errors.Errorf("failed to decrypt key: %w", err)
	}

	return decryptedKey, nil
}

// TODO: zero the child keys derived per input, here and in FullySign.
func buildSignedSweepTx(
	utxos []*scanner.Utxo,
	unsignedSweepTx *wire.MsgTx,
	userKey *libwallet.HDPrivateKey,
	cosignerKey *libwallet.HDPrivateKey,
) (*wire.MsgTx, error) {
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

	signedTx, err := pstx.FullySign(userKey, cosignerKey)
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
