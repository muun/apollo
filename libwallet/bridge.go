package libwallet

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha1"
	"github.com/btcsuite/btcd/btcec/v2"
)

type StringList struct {
	elems []string
}

func NewStringList() *StringList {
	return &StringList{}
}

func NewStringListWithElements(elems []string) *StringList {
	return &StringList{elems}
}

func (l *StringList) Length() int {
	return len(l.elems)
}

func (l *StringList) Get(index int) string {
	return l.elems[index]
}

func (l *StringList) Add(s string) {
	l.elems = append(l.elems, s)
}

func (l *StringList) Contains(s string) bool {
	for _, v := range l.elems {
		if v == s {
			return true
		}
	}

	return false
}

func (l *StringList) ConvertToArray() []string {
	return l.elems
}

type IntList struct {
	elems []int
}

func NewIntList() *IntList {
	return &IntList{}
}

func newIntList(elems []int) *IntList {
	return &IntList{elems}
}

func (l *IntList) Length() int {
	return len(l.elems)
}

func (l *IntList) Get(index int) int {
	return l.elems[index]
}

func (l *IntList) Add(number int) {
	l.elems = append(l.elems, number)
}

func (l *IntList) Contains(number int) bool {
	for _, v := range l.elems {
		if v == number {
			return true
		}
	}

	return false
}

// TODO: Remove when delete security cards POC
func EncryptHMacSha1(key []byte, text []byte) []byte {
	h := hmac.New(sha1.New, key)
	h.Write(text)
	return h.Sum(nil)
}

// TODO: Remove when delete security cards POC
func GenerateSharedSecret(privateKey *HDPrivateKey, publicKeyBytes []byte) []byte {
	publicKey, _ := btcec.ParsePubKey(publicKeyBytes)
	privateKeyFinal, _ := privateKey.key.ECPrivKey()
	return btcec.GenerateSharedSecret(privateKeyFinal, publicKey)
}

// TODO: Remove when delete security cards POC
func AesEncrypt(key []byte, iv []byte, plaintext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	ciphertext := make([]byte, len(plaintext))

	mode := cipher.NewCBCEncrypter(block, iv)
	mode.CryptBlocks(ciphertext, plaintext)

	return ciphertext, nil
}

// TODO: Remove when delete security cards POC
func (p *HDPublicKey) SerializeUncompressed() []byte {
	key, err := p.key.ECPubKey()
	if err != nil {
		panic("failed to extract pub key")
	}

	return key.SerializeUncompressed()
}
