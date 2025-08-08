package bitcoin_hpke

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"golang.org/x/crypto/chacha20poly1305"
	"slices"
)

// This package implements HPKE(DHKEM(secp256k1, HKDF-SHA256), HKDF-SHA256, Chacha20Poly1305) in base, single shot mode.
// Note that DHKEM(secp256k1, HKDF-SHA256) is not part of HPKE as described in RFC-9180 but of a proposed extension by
// R.S. Wahby (https://www.ietf.org/archive/id/draft-wahby-cfrg-hpke-kem-secp256k1-01.html).
// This HPKE configuration is notably used in the Payjoin protocol (https://github.com/payjoin/bitcoin-hpke).

const (
	hpkeIdentifier = "HPKE-v1"

	baseMode     = 0x00 // Table 1 of RFC 9180
	defaultPsk   = ""   // Section 5.1 of RFC 9180
	defaultPskId = ""   // Section 5.1 of RFC 9180

	// KEM constants (see Wahby's Internet-Draft)
	kemId                                  = 0x0016 // This is DHKEM(secp256k1, HKDF-SHA256)
	privateKeyLengthInBytes                = 32     // The length in bytes of a KEM shared secret
	encapsulatedKeyLengthInBytes           = 65     // The length in bytes of an encapsulated key
	diffieHellmanSharedSecretLengthInBytes = 32     // The length in bytes of a Diffie-Hellman shared secret

	// KDF constants (see Table 3 of RFC 9180)
	kdfId = 0x0001 // This is HKDF-SHA256

	// AEAD constants (see Table 5 of RFC 9180)
	aeadId                         = 0x0003 // This is Chacha20Poly1305
	keyLengthInBytes               = 32     // The length in bytes of a key
	nonceLengthInBytes             = 12     // The length in bytes of a nonce
	authenticationTagLengthInBytes = 16     // The length in bytes of an authentication tag
)

// Encrypt with bitcoin-HPKE in single shot base mode
func SingleShotEncrypt(
	plaintext []byte,
	receiverPublicKey *btcec.PublicKey,
	info []byte,
	additionalAuthenticatedData []byte,
) (*EncryptedMessage, error) {

	encapsulatedKey, sharedSecret, err := encapsulate(receiverPublicKey)
	if err != nil {
		return nil, err
	}

	key, baseNonce, err := keyScheduleBase(sharedSecret, info)
	if err != nil {
		return nil, err
	}

	aead, err := chacha20poly1305.New(key)
	if err != nil {
		return nil, err
	}

	// Sealing with a nil value for the dst parameter has the effect of allocating a new slice for the result
	ciphertext := aead.Seal(nil, baseNonce, plaintext, additionalAuthenticatedData)

	return &EncryptedMessage{
		encapsulatedKey: encapsulatedKey,
		ciphertext:      ciphertext,
	}, nil
}

// Decrypt bitcoin-HPKE in single shot base mode
func (encryptedMessage EncryptedMessage) SingleShotDecrypt(
	receiverPrivateKey *btcec.PrivateKey,
	info []byte,
	additionalAuthenticatedData []byte,
) ([]byte, error) {

	sharedSecret, err := decapsulate(receiverPrivateKey, encryptedMessage.encapsulatedKey)
	if err != nil {
		return nil, err
	}

	key, baseNonce, err := keyScheduleBase(sharedSecret, info)
	if err != nil {
		return nil, err
	}

	aead, err := chacha20poly1305.New(key)
	if err != nil {
		return nil, err
	}

	// Opening with a nil value for the dst parameter has the effect of allocating a new slice for the result
	plaintext, err := aead.Open(nil, baseNonce, encryptedMessage.ciphertext, additionalAuthenticatedData)

	return normalize(plaintext), err
}

// See Section 5.1 of RFC 9180
func keyScheduleBase(sharedSecret, info []byte) (key, baseNonce []byte, err error) {

	suiteId := slices.Concat(
		[]byte("HPKE"),
		i2Osp(kemId, 2),
		i2Osp(kdfId, 2),
		i2Osp(aeadId, 2),
	)

	pskIdHash := labeledExtract([]byte(""), []byte("psk_id_hash"), []byte(defaultPskId), suiteId)

	infoHash := labeledExtract([]byte(""), []byte("info_hash"), info, suiteId)

	keyScheduleContext := slices.Concat(i2Osp(baseMode, 1), pskIdHash, infoHash)

	secret := labeledExtract(sharedSecret, []byte("secret"), []byte(defaultPsk), suiteId)

	key, err = labeledExpand(secret, []byte("key"), keyScheduleContext, keyLengthInBytes, suiteId)
	if err != nil {
		return nil, nil, err
	}

	baseNonce, err = labeledExpand(secret, []byte("base_nonce"), keyScheduleContext, nonceLengthInBytes, suiteId)
	if err != nil {
		return nil, nil, err
	}

	return key, baseNonce, nil
}

// Normalize so that a nil byte string is replaced by a zero length non-nil byte string
func normalize(bs []byte) []byte {
	if bs == nil {
		return []byte{}
	}
	return bs
}
