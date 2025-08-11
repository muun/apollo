package encrypted_cosigning_key

import (
	"encoding/base64"
	"errors"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/muun/libwallet/encryption"
	"log/slog"
	"math/big"
	"testing"
)

type VerifiableCosigningKey struct {
	EphemeralPublicKey       btcec.PublicKey
	PaddedServerCosigningKey btcec.PrivateKey
	Proof                    string
}

// Return the base64 encoded encrypted server cosigning key that can be decrypted with the recovery code private key.
// The boolean return value is true if the cosigning key was proven to be correct with a zero-knowledge proof.
func ComputeVerifiedEncryptedServerCosigningKey(
	serverCosigningPublicKey *hdkeychain.ExtendedKey,
	verifiableCosigningKey VerifiableCosigningKey,
	recoveryCodePublicKey *btcec.PublicKey,
) (string, bool, error) {

	serverCosigningBtcecPubKey, err := serverCosigningPublicKey.ECPubKey()
	if err != nil {
		return "", false, err
	}

	var sharedSecretPublicKey = subtractPublicKeys(verifiableCosigningKey.PaddedServerCosigningKey.PubKey(), serverCosigningBtcecPubKey)

	var verified bool
	if verifiableCosigningKey.Proof == "" {
		verified = false
	} else {
		err = plonky2ServerKeyVerify(verifiableCosigningKey, sharedSecretPublicKey, recoveryCodePublicKey)
		// TODO For now, a verification error results in an unverified key, without impeding to generate an encrypted
		//  key. This will change in the future.
		verified = err == nil
		slog.Error("error during plonky2 verify", slog.Any("error", err))
	}

	chaincodeEphemeralPrivateKey, err := btcec.NewPrivateKey()
	if err != nil {
		return "", false, err
	}
	paddedChainCode, err := paddingEncrypt(
		serverCosigningPublicKey.ChainCode(),
		recoveryCodePublicKey,
		chaincodeEphemeralPrivateKey,
	)
	if err != nil {
		return "", false, err
	}

	key := encryptedKey{
		version,
		&verifiableCosigningKey.PaddedServerCosigningKey,
		&verifiableCosigningKey.EphemeralPublicKey,
		paddedChainCode,
		chaincodeEphemeralPrivateKey.PubKey(),
	}

	return key.serialize(), verified, nil
}

func plonky2ServerKeyVerify(
	verifiableCosigningKey VerifiableCosigningKey,
	plaintextPublicKey *btcec.PublicKey,
	recoveryCodePublicKey *btcec.PublicKey,
) error {

	if testing.Testing() && verifiableCosigningKey.Proof == "mock_proof" {
		return nil
	}

	proofBytes, err := base64.StdEncoding.DecodeString(verifiableCosigningKey.Proof)
	if err != nil {
		return err
	}

	// TODO we should be calling librs here
	_ = proofBytes
	result := "error: not implemented"

	if result == "ok" {
		return nil
	} else {
		return errors.New(result)
	}
}

// Compute the subtraction A - B
func subtractPublicKeys(A, B *btcec.PublicKey) *btcec.PublicKey {
	// Recall that -B is given by (B.X, -B.Y). Note also that since B is on the curve, B.Y cannot be zero and therefore
	// P-B.Y is already reduced modulo P. Thus there is no need to reduce modulo P in the line below.
	rX, rY := btcec.S256().Add(A.X(), A.Y(), B.X(), new(big.Int).Sub(btcec.S256().P, B.Y()))
	var X, Y btcec.FieldVal
	X.SetByteSlice(encryption.PaddedSerializeBigInt(32, rX))
	Y.SetByteSlice(encryption.PaddedSerializeBigInt(32, rY))
	return btcec.NewPublicKey(&X, &Y)
}
