package encrypted_key_v3

import (
	"bytes"
	"encoding/base64"
	"slices"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

const (
	version            = 3
	privateKeyLenBytes = btcec.PrivKeyBytesLen
	chainCodeLenBytes  = 32

	userFirstHalfToRecoveryCode     = "muun.com/cosigning-key/1/1/recovery-code"
	userSecondHalfToRecoveryCode    = "muun.com/cosigning-key/1/2/recovery-code"
	cosignerFirstHalfToRecoveryCode = "muun.com/cosigning-key/2/1/recovery-code"
)

type keyBearer uint8

const (
	user keyBearer = iota + 1
	cosigner
)

func FinishCosignerKeyEncryption(
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
		[]byte(cosignerFirstHalfToRecoveryCode),
		[]byte(""),
	)

	if err != nil {
		return "", errors.Errorf(
			"encrypt extended key: failed for first encrypted message: %w", err,
		)
	}

	k := newEncryptedKey(
		version,
		cosigner,
		firstEncryptedMessage,
		secondHalfKeyEncryptedToRecoveryCode,
	)
	return k.serialize(), nil
}

// TODO: zero privateKey, the a and b shares and the plaintexts handed to HPKE
// once they are encrypted.
func EncryptUserKey(
	userPrivateKey *hd_keys.HDPrivateKey,
	recoveryCodePublicKey *btcec.PublicKey,
) (string, error) {

	privateKey, err := userPrivateKey.ECPrivKey()
	if err != nil {
		return "", errors.Errorf("encrypt extended key: failed to extract private key %w", err)
	}

	a, err := btcec.NewPrivateKey()
	if err != nil {
		return "", errors.Errorf("encrypt extended key: failed to create new private key %w", err)
	}
	b := new(btcec.ModNScalar).Set(&a.Key).Negate().Add(&privateKey.Key).Bytes()

	firstEncryptedMessage, err := bitcoin_hpke.SingleShotEncrypt(
		slices.Concat(a.Serialize(), userPrivateKey.ChainCode()),
		recoveryCodePublicKey,
		[]byte(userFirstHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		return "", errors.Errorf(
			"encrypt extended key: failed for first encrypted message: %w", err,
		)
	}

	secondEncryptedMessage, err := bitcoin_hpke.SingleShotEncrypt(
		b[:],
		recoveryCodePublicKey,
		[]byte(userSecondHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		return "", errors.Errorf(
			"encrypt extended key: failed for second encrypted message: %w", err,
		)
	}

	ek := newEncryptedKey(version, user, firstEncryptedMessage, secondEncryptedMessage)
	return ek.serialize(), nil
}

func DecryptExtendedKey(
	recoveryCodePrivateKey *btcec.PrivateKey,
	encryptedExtendedKey string,
	network *chaincfg.Params,
) (*hd_keys.HDPrivateKey, error) {

	key, err := deserializeEncryptedKeyV3(encryptedExtendedKey)
	if err != nil {
		return nil, err
	}

	var firstHalfKey, secondHalfKey btcec.ModNScalar

	var infoForFirstHalf string
	if key.bearer == user {
		infoForFirstHalf = userFirstHalfToRecoveryCode
	} else {
		infoForFirstHalf = cosignerFirstHalfToRecoveryCode
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
		infoForSecondHalf = recovery_model.CosignerSecondHalfToRecoveryCode
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

	return hd_keys.NewBasePathHDPrivateKeyFromBytes(privateKey[:], chainCode, network), nil
}

type encryptedKey struct {
	version                uint8
	bearer                 keyBearer
	firstEncryptedMessage  *bitcoin_hpke.EncryptedMessage
	secondEncryptedMessage *bitcoin_hpke.EncryptedMessage
}

func newEncryptedKey(
	version uint8,
	bearer keyBearer,
	first *bitcoin_hpke.EncryptedMessage,
	second *bitcoin_hpke.EncryptedMessage,
) *encryptedKey {
	return &encryptedKey{
		version:                version,
		bearer:                 bearer,
		firstEncryptedMessage:  first,
		secondEncryptedMessage: second,
	}
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
		return nil, errors.Errorf("decrypting key: failed to decode from base64 %w", err)
	}
	reader := bytes.NewReader(serializedKeyBytes)

	versionByte, err := reader.ReadByte()
	if err != nil {
		return nil, errors.Errorf("decrypting key: failed to read version %w", err)
	}

	if versionByte != 3 {
		return nil, errors.Errorf(
			"decrypting key: expected a v3 key, version byte indicates v%d",
			versionByte,
		)
	}

	bearerByte, err := reader.ReadByte()
	if err != nil {
		return nil, errors.Errorf("decrypting key: failed to read bearer %w", err)
	}

	bearer, err := validateBearerByte(bearerByte)
	if err != nil {
		return nil, err
	}

	firstEncryptedMessageLenInBytes := bitcoin_hpke.SerializedEncryptedMessageLengthInBytes(
		privateKeyLenBytes + chainCodeLenBytes,
	)
	firstEncryptedMessageBytes := make([]byte, firstEncryptedMessageLenInBytes)
	n, err := reader.Read(firstEncryptedMessageBytes[:])
	if err != nil || n != firstEncryptedMessageLenInBytes {
		return nil, errors.Errorf("decrypting key: failed to read firstEncryptedMessage %w", err)
	}
	firstEncryptedMessage, err := bitcoin_hpke.ParseEncryptedMessage(firstEncryptedMessageBytes[:])
	if err != nil {
		return nil, errors.Errorf("decrypting key: failed to parse firstEncryptedMessage %w", err)
	}

	secondEncryptedMessageLenInBytes := bitcoin_hpke.SerializedEncryptedMessageLengthInBytes(
		privateKeyLenBytes,
	)
	secondEncryptedMessageBytes := make([]byte, secondEncryptedMessageLenInBytes)
	n, err = reader.Read(secondEncryptedMessageBytes[:])
	if err != nil || n != secondEncryptedMessageLenInBytes {
		return nil, errors.Errorf("decrypting key: failed to read secondEncryptedMessage %w", err)
	}
	secondEncryptedMessage, err := bitcoin_hpke.ParseEncryptedMessage(
		secondEncryptedMessageBytes[:],
	)
	if err != nil {
		return nil, errors.Errorf("decrypting key: failed to parse secondEncryptedMessage %w", err)
	}

	if reader.Len() > 0 {
		return nil, errors.New("decrypting key: key is longer than expected")
	}

	return newEncryptedKey(versionByte, bearer, firstEncryptedMessage, secondEncryptedMessage), nil
}

func validateBearerByte(bearerByte uint8) (keyBearer, error) {
	bearer := keyBearer(bearerByte)
	switch bearer {
	case user, cosigner:
		return bearer, nil
	default:
		return bearer, errors.Errorf("invalid value for key bearer byte: %d", bearerByte)
	}
}
