package bitcoin_hpke

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/encryption"
	"slices"
	"testing"
)

// See Section 4.1 of RFC 9180
func encapsulate(
	receiverPublicKey *btcec.PublicKey,
) (encapsulatedKey *btcec.PublicKey, sharedSecret []byte, err error) {
	ephemeralPrivateKey, ephemeralPublicKey, err := generateKeyPair()
	if err != nil {
		return nil, nil, err
	}
	dh := diffieHellman(ephemeralPrivateKey, receiverPublicKey)
	kemContext := slices.Concat(ephemeralPublicKey.SerializeUncompressed(), receiverPublicKey.SerializeUncompressed())
	sharedSecret, err = extractAndExpand(dh, kemContext)
	if err != nil {
		return nil, nil, err
	}
	return ephemeralPublicKey, sharedSecret, nil
}

// See Section 4.1 of RFC 9180
func decapsulate(
	receiverPrivateKey *btcec.PrivateKey,
	encapsulatedKey *btcec.PublicKey,
) (sharedSecret []byte, err error) {

	dh := diffieHellman(receiverPrivateKey, encapsulatedKey)
	kemContext := slices.Concat(encapsulatedKey.SerializeUncompressed(), receiverPrivateKey.PubKey().SerializeUncompressed())
	return extractAndExpand(dh, kemContext)
}

func generateKeyPair() (*btcec.PrivateKey, *btcec.PublicKey, error) {

	// For testing purposes we generate predetermined ephemeral keys.
	if testing.Testing() && testingOnlyGenerateKeyPair != nil {
		return testingOnlyGenerateKeyPair()
	}

	privateKey, err := btcec.NewPrivateKey()
	if err != nil {
		return nil, nil, err
	}

	return privateKey, privateKey.PubKey(), nil
}

var testingOnlyGenerateKeyPair func() (*btcec.PrivateKey, *btcec.PublicKey, error)

func diffieHellman(privateKey *btcec.PrivateKey, publicKey *btcec.PublicKey) []byte {
	sharedSecret, _ := btcec.S256().ScalarMult(publicKey.X(), publicKey.Y(), privateKey.ToECDSA().D.Bytes())

	return encryption.PaddedSerializeBigInt(diffieHellmanSharedSecretLengthInBytes, sharedSecret)
}

// See Section 4.1 of RFC 9180
func extractAndExpand(dh []byte, kemContext []byte) ([]byte, error) {
	suiteId := slices.Concat([]byte("KEM"), i2Osp(kemId, 2))
	eaePrk := labeledExtract([]byte(""), []byte("eae_prk"), dh, suiteId)

	return labeledExpand(eaePrk, []byte("shared_secret"), kemContext, privateKeyLengthInBytes, suiteId)
}
