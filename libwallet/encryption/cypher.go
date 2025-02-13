package encryption

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet/hdpath"
)

const serializedPublicKeyLength = btcec.PubKeyBytesLenCompressed

const (
	PKEncryptionVersionV1 = 1
	// PKEncryptionVersionV2 is functionally equivalent to V1, except we guarantee it doesn't
	// have buggy hardened derivation keys.
	PKEncryptionVersionV2 = 2
)

// maxDerivationPathLen is a safety limit to avoid stupid size allocations
const maxDerivationPathLen = 1000

// maxSignatureLen is a safety limit to avoid giant allocations
const maxSignatureLen = 200

// minNonceLen is the safe minimum we'll set for the nonce. This is the default for golang, but it's not exposed.
const minNonceLen = 12

type HdPubKeyEncrypter struct {
	ReceiverKey     *btcec.PublicKey
	ReceiverKeyPath string
	SenderKey       *btcec.PrivateKey
}

func (e *HdPubKeyEncrypter) Encrypt(payload []byte) (string, error) {
	// Uses AES128-GCM with associated data. ECDHE is used for key exchange and ECDSA for authentication.
	// The goal is to be able to send an arbitrary message to a 3rd party or our future selves via
	// an intermediary which has knowledge of public keys for all parties involved.
	//
	// Conceptually, what we do is:
	// 1. Sign the payload using the senders private key so the receiver can check it's authentic
	// The signature also covers the receivers public key to avoid payload reuse by the intermediary
	// 2. Establish an encryption key via ECDH given the receivers pub key
	// 3. Encrypt the payload and signature using AES with a new random nonce
	// 4. Add the metadata the receiver will need to decode the message:
	//   * The derivation path for his pub key
	//   * The ephemeral key used for ECDH
	//   * The version code of this scheme
	// 5. HMAC the encrypted payload and the metadata so the receiver can check it hasn't been tampered
	// 6. Add the nonce to the payload so the receiver can actually decrypt the message.
	// The nonce can't be covered by the HMAC since it's used to generate it.
	// 7. Profit!
	//
	// The implementation actually use an AES128-GCM with is an AEAD, so the encryption and HMAC all happen
	// at the same time.

	signingKey := e.SenderKey
	encryptionKey := e.ReceiverKey

	// Sign "payload || encryptionKey" to protect against payload reuse by 3rd parties
	signaturePayload := make([]byte, 0, len(payload)+serializedPublicKeyLength)
	signaturePayload = append(signaturePayload, payload...)
	signaturePayload = append(signaturePayload, encryptionKey.SerializeCompressed()...)
	hash := sha256.Sum256(signaturePayload)
	senderSignature, err := ecdsa.SignCompact(signingKey, hash[:], false)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to sign payload: %w", err)
	}

	// plaintext is "senderSignature || payload"
	plaintext := bytes.NewBuffer(make([]byte, 0, 2+len(payload)+2+len(senderSignature)))
	err = addVariableBytes(plaintext, senderSignature)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to add senderSignature: %w", err)
	}

	err = addVariableBytes(plaintext, payload)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to add payload: %w", err)
	}

	pubEph, sharedSecret, err := GenerateSharedEncryptionSecretForAES(encryptionKey)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to generate shared encryption key: %w", err)
	}

	blockCipher, err := aes.NewCipher(sharedSecret)
	if err != nil {
		return "", fmt.Errorf("Encrypt: new aes failed: %w", err)
	}

	gcm, err := cipher.NewGCM(blockCipher)
	if err != nil {
		return "", fmt.Errorf("Encrypt: new gcm failed: %w", err)
	}

	nonce := make([]byte, gcm.NonceSize())
	_, err = rand.Read(nonce)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to generate nonce: %w", err)
	}

	// additionalData is "version || pubEph || ReceiverKeyPath || nonceLen"
	additionalDataLen := 1 + serializedPublicKeyLength + 2 + len(e.ReceiverKeyPath) + 2
	result := bytes.NewBuffer(make([]byte, 0, additionalDataLen))
	result.WriteByte(PKEncryptionVersionV2)
	result.Write(pubEph.SerializeCompressed())

	err = addVariableBytes(result, []byte(e.ReceiverKeyPath))
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to add receiver path: %w", err)
	}

	nonceLen := uint16(len(nonce))
	err = binary.Write(result, binary.BigEndian, &nonceLen)
	if err != nil {
		return "", fmt.Errorf("Encrypt: failed to add nonce len: %w", err)
	}

	ciphertext := gcm.Seal(nil, nonce, plaintext.Bytes(), result.Bytes())

	// result is "additionalData || nonce || ciphertext"
	n, err := result.Write(nonce)
	if err != nil || n != len(nonce) {
		return "", errors.New("Encrypt: failed to add nonce")
	}

	n, err = result.Write(ciphertext)
	if err != nil || n != len(ciphertext) {
		return "", errors.New("Encrypt: failed to add ciphertext")
	}

	return base58.Encode(result.Bytes()), nil
}

type KeyProvider interface {
	WithPath(path string) (*btcec.PrivateKey, error)
	WithPathUsingHardenedBug(path string) (*btcec.PrivateKey, error)
	Path() string
}

// hdPrivKeyDecrypter holds the keys for validation and decryption of messages using Muun's scheme
type HdPrivKeyDecrypter struct {
	KeyProvider KeyProvider

	// SenderKey optionally holds the pub key used by sender
	// If the sender is the same as the receiver, set this to nil and set FromSelf to true.
	// If the sender is unknown, set this to nil. If so, the authenticity of the message won't be validated.
	SenderKey *btcec.PublicKey

	// FromSelf is true if this message is from yourself
	FromSelf bool
}

func (d *HdPrivKeyDecrypter) Decrypt(payload string) ([]byte, error) {
	// Uses AES128-GCM with associated data. ECDHE is used for key exchange and ECDSA for authentication.
	// See Encrypt further up for an in depth dive into the scheme used

	slog.Info("Libwallet: Decrypting payload " + payload)

	parsed, err := parseEncodedPayload(payload)
	if err != nil {
		return nil, fmt.Errorf("Decrypt: failed to parse payload: %w", err)
	}

	encryptionKey, verificationKey, err := d.computeKeys(parsed, false)
	if err != nil {
		return nil, fmt.Errorf("Decrypt: failed to compute keys: %w", err)
	}

	data, err := parsed.decryptAndVerify(encryptionKey, verificationKey)
	if err != nil {
		// Save the error why the first attempt failed so we can return it we
		// shouldn't attempt again.
		originalError := err

		shouldTryDerivationWithBug, err := d.isPotentiallyAffectedByImplicitHardenedDerivationBug(parsed)
		if err != nil {
			return nil, fmt.Errorf(
				"Decrypt: failed to check if affected by derivation bug: %w"+
					" -- originally failed due to %w", err, originalError,
			)
		}

		if !shouldTryDerivationWithBug {
			return nil, originalError
		}

		encryptionKey, verificationKey, err = d.computeKeys(parsed, true)
		if err != nil {
			return nil, fmt.Errorf("Decrypt: failed to compute keys with derivation bug: %w", err)
		}

		data, err = parsed.decryptAndVerify(encryptionKey, verificationKey)
		if err != nil {
			return nil, fmt.Errorf("Decrypt: failed to decrypt payload with derivation bug: %w", err)
		}

		return data, nil
	}

	return data, nil
}

func (d *HdPrivKeyDecrypter) computeKeys(parsed decodedPayload, useHardenedDerivationWithBug bool) (*btcec.PrivateKey, *btcec.PublicKey, error) {
	var err error
	var verificationKey *btcec.PublicKey

	var receiverKey *btcec.PrivateKey
	if useHardenedDerivationWithBug {
		receiverKey, err = d.KeyProvider.WithPathUsingHardenedBug(parsed.receiverPath)
	} else {
		receiverKey, err = d.KeyProvider.WithPath(parsed.receiverPath)
	}
	if err != nil {
		return nil, nil, fmt.Errorf("computeKeys: failed to derive receiver key to path %v: %w", parsed.receiverPath, err)
	}

	if d.FromSelf {
		// Use the derived receiver key if the sender key is not provided
		verificationKey = receiverKey.PubKey()
	} else if d.SenderKey != nil {
		verificationKey = d.SenderKey
	}

	return receiverKey, verificationKey, nil
}

func (d *HdPrivKeyDecrypter) isPotentiallyAffectedByImplicitHardenedDerivationBug(parsed decodedPayload) (bool, error) {
	if parsed.version != PKEncryptionVersionV1 {
		return false, nil
	}

	receiverKeyPath, err := hdpath.Parse(d.KeyProvider.Path())
	if err != nil {
		return false, fmt.Errorf("isPotentiallyAffectedByImplicitHardenedDerivationBug: error parsing ReceiverKey path %w", err)
	}

	pathToDeriveTo, err := hdpath.Parse(parsed.receiverPath)
	if err != nil {
		return false, fmt.Errorf("isPotentiallyAffectedByImplicitHardenedDerivationBug: error parsing receiverPath %w", err)
	}

	for _, index := range pathToDeriveTo.IndexesFrom(receiverKeyPath) {
		if index.Hardened {
			return true, nil
		}
	}

	return false, nil
}

type decodedPayload struct {
	version        uint8
	rawPubEph      []byte
	receiverPath   string
	nonce          []byte
	ciphertext     []byte
	additionalData []byte
}

func parseEncodedPayload(payload string) (decodedPayload, error) {
	decoded := base58.Decode(payload)
	reader := bytes.NewReader(decoded)
	version, err := reader.ReadByte()
	if err != nil {
		return decodedPayload{}, fmt.Errorf("parseEncodedPayload: failed to read version byte: %w", err)
	}
	if version != PKEncryptionVersionV1 && version != PKEncryptionVersionV2 {
		return decodedPayload{}, fmt.Errorf("parseEncodedPayload: found key version %v", version)
	}

	rawPubEph := make([]byte, serializedPublicKeyLength)
	n, err := reader.Read(rawPubEph)
	if err != nil || n != serializedPublicKeyLength {
		return decodedPayload{}, errors.New("parseEncodedPayload: failed to read pubeph")
	}

	receiverPath, err := extractVariableString(reader, maxDerivationPathLen)
	if err != nil {
		return decodedPayload{}, fmt.Errorf("parseEncodedPayload: failed to extract receiver path: %w", err)
	}

	// additionalDataSize is Whatever I've read so far plus two bytes for the nonce len
	additionalDataSize := len(decoded) - reader.Len() + 2

	minCiphertextLen := 2 // an empty sig with no plaintext
	nonce, err := extractVariableBytes(reader, reader.Len()-minCiphertextLen)
	if err != nil || len(nonce) < minNonceLen {
		return decodedPayload{}, errors.New("parseEncodedPayload: failed to read nonce")
	}

	// What's left is the ciphertext
	ciphertext := make([]byte, reader.Len())
	_, err = reader.Read(ciphertext)
	if err != nil {
		return decodedPayload{}, fmt.Errorf("parseEncodedPayload: failed to read ciphertext: %w", err)
	}

	additionalData := decoded[:additionalDataSize]

	return decodedPayload{
		version,
		rawPubEph,
		receiverPath,
		nonce,
		ciphertext,
		additionalData,
	}, nil
}

func (p decodedPayload) decryptAndVerify(encryptionKey *btcec.PrivateKey, verificationKey *btcec.PublicKey) ([]byte, error) {
	sharedSecret, err := RecoverSharedEncryptionSecretForAES(encryptionKey, p.rawPubEph)
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: failed to recover shared secret: %w", err)
	}

	blockCipher, err := aes.NewCipher(sharedSecret)
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: new aes failed: %w", err)
	}

	gcm, err := cipher.NewGCMWithNonceSize(blockCipher, len(p.nonce))
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: new gcm failed: %w", err)
	}

	plaintext, err := gcm.Open(nil, p.nonce, p.ciphertext, p.additionalData)
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: AEAD failed: %w", err)
	}

	plaintextReader := bytes.NewReader(plaintext)

	sig, err := extractVariableBytes(plaintextReader, maxSignatureLen)
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: failed to read sig: %w", err)
	}

	data, err := extractVariableBytes(plaintextReader, plaintextReader.Len())
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: failed to extract user data: %w", err)
	}

	signatureData := make([]byte, 0, len(sig)+serializedPublicKeyLength)
	signatureData = append(signatureData, data...)
	signatureData = append(signatureData, encryptionKey.PubKey().SerializeCompressed()...)
	hash := sha256.Sum256(signatureData)
	signatureKey, _, err := ecdsa.RecoverCompact(sig, hash[:])
	if err != nil {
		return nil, fmt.Errorf("decryptAndVerify: failed to verify signature: %w", err)
	}
	if verificationKey != nil && !signatureKey.IsEqual(verificationKey) {
		return nil, errors.New("decryptAndVerify: signing key mismatch")
	}

	return data, nil
}
