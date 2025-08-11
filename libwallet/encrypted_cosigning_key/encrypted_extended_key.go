package encrypted_cosigning_key

import (
	"bytes"
	"encoding/base64"
	"errors"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/recoverycode"
)

const (
	version                     uint8 = 3
	privateKeyLenBytes                = btcec.PrivKeyBytesLen
	compressedPublicKeyLenBytes       = btcec.PubKeyBytesLenCompressed
	chainCodeLenBytes                 = 32
)

type encryptedKey struct {
	version                     uint8
	paddedPrivateKey            *btcec.PrivateKey
	ephemeralPublicKey          *btcec.PublicKey
	paddedChainCode             []byte
	ephemeralChainCodePublicKey *btcec.PublicKey
}

func (key *encryptedKey) serialize() string {
	result := make(
		[]byte,
		0,
		1+privateKeyLenBytes+compressedPublicKeyLenBytes+chainCodeLenBytes+compressedPublicKeyLenBytes,
	)

	buf := bytes.NewBuffer(result)

	buf.WriteByte(version)
	buf.Write(key.paddedPrivateKey.Serialize())
	buf.Write(key.ephemeralPublicKey.SerializeCompressed())
	buf.Write(key.paddedChainCode)
	buf.Write(key.ephemeralChainCodePublicKey.SerializeCompressed())

	return base64.StdEncoding.EncodeToString(buf.Bytes())
}

func deserializeEncryptedKey(serializedKey string) (*encryptedKey, error) {

	serializedKeyBytes, err := base64.StdEncoding.DecodeString(serializedKey)
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to decode from base64 %w", err)
	}
	reader := bytes.NewReader(serializedKeyBytes)

	version, err := reader.ReadByte()

	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to read version %w", err)
	}

	paddedPrivateKeyBytes := make([]byte, privateKeyLenBytes)
	ephemeralPublicKeyBytes := make([]byte, compressedPublicKeyLenBytes)
	paddedChainCode := make([]byte, chainCodeLenBytes)
	ephemeralChainCodePublicKeyBytes := make([]byte, compressedPublicKeyLenBytes)

	n, err := reader.Read(paddedPrivateKeyBytes)
	if err != nil || n != privateKeyLenBytes {
		return nil, fmt.Errorf("decrypting key: failed to read paddedPrivateKey %w", err)
	}
	paddedPrivateKey, _ := btcec.PrivKeyFromBytes(paddedPrivateKeyBytes)

	n, err = reader.Read(ephemeralPublicKeyBytes)
	if err != nil || n != compressedPublicKeyLenBytes {
		return nil, fmt.Errorf("decrypting key: failed to read ephemeralPublicKey %w", err)
	}
	ephemeralPublicKey, err := btcec.ParsePubKey(ephemeralPublicKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to parse ephemeralPublicKey %w", err)
	}

	n, err = reader.Read(paddedChainCode)
	if err != nil || n != chainCodeLenBytes {
		return nil, fmt.Errorf("decrypting key: failed to read chainCode %w", err)
	}

	n, err = reader.Read(ephemeralChainCodePublicKeyBytes)
	if err != nil || n != compressedPublicKeyLenBytes {
		return nil, fmt.Errorf("decrypting key: failed to read ephemeralChainCodePublicKey %w", err)
	}
	ephemeralChainCodePublicKey, err := btcec.ParsePubKey(ephemeralChainCodePublicKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to parse ephemeralChainCodePublicKey %w", err)
	}

	if reader.Len() > 0 {
		return nil, errors.New("decrypting key: key is longer than expected")
	}

	return &encryptedKey{
		version,
		paddedPrivateKey,
		ephemeralPublicKey,
		paddedChainCode,
		ephemeralChainCodePublicKey,
	}, nil
}

func EncryptExtendedKey(key *libwallet.HDPrivateKey, recoveryCodePublicKey *btcec.PublicKey) (string, error) {

	privateKey, err := key.ECPrivateKey()
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to extract private key %w", err)
	}

	ephemeralPrivateKey, err := btcec.NewPrivateKey()
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to obtain ephemeral key %w", err)
	}

	paddedPrivateKeyBytes, err := paddingEncrypt(
		privateKey.Serialize(),
		recoveryCodePublicKey,
		ephemeralPrivateKey,
	)
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to encrypt private key %w", err)
	}
	paddedPrivateKey, _ := btcec.PrivKeyFromBytes(paddedPrivateKeyBytes)

	chaincodeEphemeralPrivateKey, err := btcec.NewPrivateKey()
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to obtain ephemeral key %w", err)
	}

	paddedChainCode, err := paddingEncrypt(
		key.ChainCode(),
		recoveryCodePublicKey,
		chaincodeEphemeralPrivateKey,
	)
	if err != nil {
		return "", err
	}

	encryptedKey := encryptedKey{
		version:                     version,
		paddedPrivateKey:            paddedPrivateKey,
		ephemeralPublicKey:          ephemeralPrivateKey.PubKey(),
		paddedChainCode:             paddedChainCode,
		ephemeralChainCodePublicKey: chaincodeEphemeralPrivateKey.PubKey(),
	}

	return encryptedKey.serialize(), nil
}

func DecryptExtendedKey(recoveryCode string, encryptedExtendedKey string, network *libwallet.Network) (*libwallet.HDPrivateKey, error) {

	key, err := deserializeEncryptedKey(encryptedExtendedKey)
	if err != nil {
		return nil, err
	}

	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		return nil, err
	}

	privateKey, err := paddingDecrypt(key.paddedPrivateKey.Serialize(), recoveryCodePrivateKey, key.ephemeralPublicKey)
	if err != nil {
		return nil, err
	}

	chainCode, err := paddingDecrypt(key.paddedChainCode, recoveryCodePrivateKey, key.ephemeralChainCodePublicKey)
	if err != nil {
		return nil, err
	}

	return libwallet.NewHDPrivateKeyFromBytes(privateKey, chainCode, network)
}
