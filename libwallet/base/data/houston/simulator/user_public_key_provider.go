package simulator

import "github.com/muun/libwallet/base/domain/cryptography/hd_keys"

// UserPublicKeyProvider hands the Houston simulator the user's base public key,
// which Houston proper holds since the wallet's first session. The simulator reads
// it when it verifies a client approval rather than when it is built, since
// libwallet starts before the wallet has a key.
type UserPublicKeyProvider interface {
	UserPublicKey() (*hd_keys.HDPublicKey, error)
}
