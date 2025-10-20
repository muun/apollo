package keys

import (
	"fmt"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
)

// Provide keys. All keys are already derived at our usual base path "m/schema:1'/recovery:1'"
type KeyProvider interface {
	UserPrivateKey() (*libwallet.HDPrivateKey, error)
	UserPublicKey() (*libwallet.HDPublicKey, error)
	MuunPublicKey() (*libwallet.HDPublicKey, error)
	EncryptedMuunPrivateKey() (*libwallet.EncryptedPrivateKeyInfo, error)
	MaxDerivedIndex() int
}

type keyProvider struct {
	keyProvider app_provided_data.KeyProvider
	network     libwallet.Network
}

func NewKeyProvider(k app_provided_data.KeyProvider, network libwallet.Network) KeyProvider {
	return &keyProvider{keyProvider: k, network: network}
}

func (p *keyProvider) UserPrivateKey() (*libwallet.HDPrivateKey, error) {
	userKeyData, err := p.keyProvider.FetchUserKey()
	if err != nil {
		return nil, err
	}

	userPrivKey, err := libwallet.NewHDPrivateKeyFromString(userKeyData.Serialized, userKeyData.Path, &p.network)
	if err != nil {
		return nil, err
	}

	return userPrivKey, nil
}

func (p *keyProvider) UserPublicKey() (*libwallet.HDPublicKey, error) {
	userPrivKey, err := p.UserPrivateKey()
	if err != nil {
		return nil, err
	}

	return userPrivKey.PublicKey(), nil
}

func (p *keyProvider) MuunPublicKey() (*libwallet.HDPublicKey, error) {
	muunKeyData, err := p.keyProvider.FetchMuunKey()
	if err != nil {
		return nil, err
	}

	muunKey, err := libwallet.NewHDPublicKeyFromString(muunKeyData.Serialized, muunKeyData.Path, &p.network)
	if err != nil {
		return nil, err
	}

	return muunKey, nil
}

func (p *keyProvider) EncryptedMuunPrivateKey() (*libwallet.EncryptedPrivateKeyInfo, error) {
	encodedKeyData, err := p.keyProvider.FetchEncryptedMuunPrivateKey()
	if err != nil {
		return nil, err
	}

	return libwallet.DecodeEncryptedPrivateKey(encodedKeyData)
}

func (p *keyProvider) DecryptMuunPrivateKey(recoveryCode string, encryptedKey *libwallet.EncryptedPrivateKeyInfo, network *libwallet.Network) (*libwallet.DecryptedPrivateKey, error) {
	salt := encryptedKey.Salt
	decryptionKey, err := libwallet.RecoveryCodeToKey(recoveryCode, salt)

	if err != nil {
		return nil, fmt.Errorf("failed to process recovery code: %w", err)
	}

	decryptedKey, err := decryptionKey.DecryptKey(encryptedKey, network)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt ke: %w", err)
	}

	return decryptedKey, nil
}

func (p *keyProvider) MaxDerivedIndex() int {
	return p.keyProvider.FetchMaxDerivedIndex()
}
