package aescbc

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"

	"github.com/pkg/errors"
)

const KeySize = 32

func EncryptPkcs7(key []byte, iv []byte, plaintext []byte) ([]byte, error) {
	plaintext = pkcs7Padding(plaintext)
	return EncryptNoPadding(key, iv, plaintext)
}

func EncryptNoPadding(key []byte, iv []byte, plaintext []byte) ([]byte, error) {
	if len(key) != KeySize {
		panic("key does not have the right size")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	ciphertext := make([]byte, len(plaintext))

	mode := cipher.NewCBCEncrypter(block, iv)
	mode.CryptBlocks(ciphertext, plaintext)

	return ciphertext, nil
}

func DecryptPkcs7(key []byte, iv []byte, cypertext []byte) ([]byte, error) {
	paddedPlaintext, err := DecryptNoPadding(key, iv, cypertext)
	if err != nil {
		return nil, err
	}

	return pkcs7UnPadding(paddedPlaintext)
}

func DecryptNoPadding(key []byte, iv []byte, cypertext []byte) ([]byte, error) {
	if len(key) != KeySize {
		panic("key does not have the right size")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	plaintext := make([]byte, len(cypertext))

	mode := cipher.NewCBCDecrypter(block, iv)
	mode.CryptBlocks(plaintext, cypertext)

	return plaintext, nil
}

func pkcs7Padding(src []byte) []byte {
	padding := aes.BlockSize - len(src)%aes.BlockSize
	padtext := bytes.Repeat([]byte{byte(padding)}, padding)
	return append(src, padtext...)
}

func pkcs7UnPadding(src []byte) ([]byte, error) {
	length := len(src)
	unpadding := int(src[length-1])

	if unpadding > aes.BlockSize || unpadding == 0 {
		return nil, errors.New("invalid pkcs7 padding (unpadding > aes.BlockSize || unpadding == 0)")
	}

	pad := src[len(src)-unpadding:]
	for i := 0; i < unpadding; i++ {
		if pad[i] != byte(unpadding) {
			return nil, errors.New("invalid pkcs7 padding (pad[i] != unpadding)")
		}
	}

	return src[:(length - unpadding)], nil
}
