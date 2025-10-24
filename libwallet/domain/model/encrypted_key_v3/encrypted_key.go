package encrypted_key_v3

import (
	"bytes"
	"encoding/base64"
	"errors"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/cryptography/bitcoin_hpke"
	"slices"
)

const (
	version            = 3
	privateKeyLenBytes = btcec.PrivKeyBytesLen
	chainCodeLenBytes  = 32
)

func FinishMuunKeyEncryption(
	recoveryCodePublicKey *btcec.PublicKey,
	firstHalfKey *btcec.PrivateKey,
	chainCode []byte,
	secondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage,
) (string, error) {

	if len(chainCode) != chainCodeLenBytes {
		return "", errors.New("chain code length must be exactly 32 bytes")
	}

	firstEncryptedMessage, err := bitcoin_hpke.SingleShotEncrypt(
		slices.Concat(firstHalfKey.Serialize(), chainCode[:]),
		recoveryCodePublicKey,
		[]byte(muunFirstHalfToRecoveryCode),
		[]byte(""),
	)

	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed for first encrypted message: %w", err)
	}

	k := encryptedKey{
		version,
		muun,
		firstEncryptedMessage,
		secondHalfKeyEncryptedToRecoveryCode,
	}

	return k.serialize(), nil
}

func EncryptUserKey(userPrivateKey *libwallet.HDPrivateKey, recoveryCodePublicKey *btcec.PublicKey) (string, error) {

	privateKey, err := userPrivateKey.ECPrivateKey()
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to extract private key %w", err)
	}

	a, err := btcec.NewPrivateKey()
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed to create new private key %w", err)
	}
	b := new(btcec.ModNScalar).Set(&a.Key).Negate().Add(&privateKey.Key).Bytes()

	firstEncryptedMessage, err := bitcoin_hpke.SingleShotEncrypt(
		slices.Concat(a.Serialize(), userPrivateKey.ChainCode()),
		recoveryCodePublicKey,
		[]byte(userFirstHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed for first encrypted message: %w", err)
	}

	secondEncryptedMessage, err := bitcoin_hpke.SingleShotEncrypt(
		b[:],
		recoveryCodePublicKey,
		[]byte(userSecondHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		return "", fmt.Errorf("encrypt extended key: failed for second encrypted message: %w", err)
	}

	ek := encryptedKey{
		version,
		user,
		firstEncryptedMessage,
		secondEncryptedMessage,
	}

	return ek.serialize(), nil
}

func DecryptExtendedKey(recoveryCodePrivateKey *btcec.PrivateKey, encryptedExtendedKey string, network *libwallet.Network) (*libwallet.HDPrivateKey, error) {

	key, err := deserializeEncryptedKeyV3(encryptedExtendedKey)
	if err != nil {
		return nil, err
	}

	var firstHalfKey, secondHalfKey btcec.ModNScalar

	var infoForFirstHalf string
	if key.bearer == user {
		infoForFirstHalf = userFirstHalfToRecoveryCode
	} else {
		infoForFirstHalf = muunFirstHalfToRecoveryCode
	}
	firstMessage, err := key.firstEncryptedMessage.SingleShotDecrypt(
		recoveryCodePrivateKey,
		[]byte(infoForFirstHalf),
		[]byte(""),
	)
	if err != nil {
		return nil, err
	}
	firstHalfKey.SetByteSlice(firstMessage[:privateKeyLenBytes])
	chainCode := firstMessage[privateKeyLenBytes:]

	var infoForSecondHalf string
	if key.bearer == user {
		infoForSecondHalf = userSecondHalfToRecoveryCode
	} else {
		infoForSecondHalf = muunSecondHalfToRecoveryCode
	}
	secondMessage, err := key.secondEncryptedMessage.SingleShotDecrypt(
		recoveryCodePrivateKey,
		[]byte(infoForSecondHalf),
		[]byte(""),
	)
	if err != nil {
		return nil, err
	}
	secondHalfKey.SetByteSlice(secondMessage)

	privateKey := firstHalfKey.Add(&secondHalfKey).Bytes()

	return libwallet.NewBasePathHDPrivateKeyFromBytes(privateKey[:], chainCode, network)
}

type encryptedKey struct {
	version                uint8
	bearer                 keyBearer
	firstEncryptedMessage  *bitcoin_hpke.EncryptedMessage
	secondEncryptedMessage *bitcoin_hpke.EncryptedMessage
}

func (key *encryptedKey) serialize() string {
	keyBytes := slices.Concat(
		[]byte{key.version},
		[]byte{uint8(key.bearer)},
		key.firstEncryptedMessage.Serialize(),
		key.secondEncryptedMessage.Serialize(),
	)

	return base64.StdEncoding.EncodeToString(keyBytes)
}

func deserializeEncryptedKeyV3(serializedKey string) (*encryptedKey, error) {

	serializedKeyBytes, err := base64.StdEncoding.DecodeString(serializedKey)
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to decode from base64 %w", err)
	}
	reader := bytes.NewReader(serializedKeyBytes)

	versionByte, err := reader.ReadByte()
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to read version %w", err)
	}

	if versionByte != 3 {
		return nil, fmt.Errorf("decrypting key: expected a v3 key, version byte indicates v%d", versionByte)
	}

	bearerByte, err := reader.ReadByte()
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to read bearer %w", err)
	}

	bearer, err := validateBearerByte(bearerByte)
	if err != nil {
		return nil, err
	}

	firstEncryptedMessageLenInBytes := bitcoin_hpke.SerializedEncryptedMessageLengthInBytes(privateKeyLenBytes + chainCodeLenBytes)
	firstEncryptedMessageBytes := make([]byte, firstEncryptedMessageLenInBytes)
	n, err := reader.Read(firstEncryptedMessageBytes[:])
	if err != nil || n != firstEncryptedMessageLenInBytes {
		return nil, fmt.Errorf("decrypting key: failed to read firstEncryptedMessage %w", err)
	}
	firstEncryptedMessage, err := bitcoin_hpke.ParseEncryptedMessage(firstEncryptedMessageBytes[:])
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to parse firstEncryptedMessage %w", err)
	}

	secondEncryptedMessageLenInBytes := bitcoin_hpke.SerializedEncryptedMessageLengthInBytes(privateKeyLenBytes)
	secondEncryptedMessageBytes := make([]byte, secondEncryptedMessageLenInBytes)
	n, err = reader.Read(secondEncryptedMessageBytes[:])
	if err != nil || n != secondEncryptedMessageLenInBytes {
		return nil, fmt.Errorf("decrypting key: failed to read secondEncryptedMessage %w", err)
	}
	secondEncryptedMessage, err := bitcoin_hpke.ParseEncryptedMessage(secondEncryptedMessageBytes[:])
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to parse secondEncryptedMessage %w", err)
	}

	if reader.Len() > 0 {
		return nil, errors.New("decrypting key: key is longer than expected")
	}

	return &encryptedKey{
		versionByte,
		bearer,
		firstEncryptedMessage,
		secondEncryptedMessage,
	}, nil
}

func validateBearerByte(bearerByte uint8) (keyBearer, error) {
	bearer := keyBearer(bearerByte)
	switch bearer {
	case user, muun:
		return bearer, nil
	default:
		return bearer, fmt.Errorf("invalid value for key bearer byte: %d", bearerByte)
	}
}
