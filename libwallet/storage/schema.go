package storage

import (
	"strconv"

	"github.com/go-errors/errors"
)

type BackupType int

const (
	NoAutoBackup BackupType = iota
	SyncAutoBackup
	AsyncAutoBackup
)

type BackupSecurity int

const (
	NotApplicable BackupSecurity = iota
	Plain
	Authenticated
	Encrypted
)

// The following key constants are a convention, not a requirement. The KV schema is
// defined by the ordered migration plan in kv_migrations.go, and new keys must be
// added there via a Define(...) change — that is the source of truth. Adding a
// constant below is only useful when libwallet Go code itself references the key
// string; Kotlin/Swift callers pass raw strings over gRPC and never see these.
const (
	KeyIsBalanceHidden            string = "isBalanceHidden"
	KeyNightMode                  string = "nightMode"
	KeySecurityCardXpubSerialized string = "securityCardXpubSerialized"
	KeyBiometricsOptIn            string = "biometricsOptIn"
	KeyPinLength                  string = "pinLength"
	// TODO: These three are marked as prototypes to avoid accidentally setting the non-prototype
	// fields in a consumer device before finalizing the design. Before production, the "Prototype"
	// suffix must be removed
	UnverifiedEncryptedMuunKey string = "unverifiedEncryptedMuungKeyPrototype"
	VerifiedEncryptedMuunKey   string = "verifiedEncryptedMuunKeyPrototype"
	EncryptedUserKey           string = "encryptedUserKeyPrototype"

	// ==== Feature flag overrides ====
	FeatureFlagOverridesNfcCardV2Key  = "featureFlagOverrides:nfcCardV2"
	FeatureFlagOverridesekGoRendering = "featureFlagOverrides:ekGoRendering"

	// ==== End of feature flag overrides ====
	// ==== Pending pair challenge (bridges PairRequestChallenge and PairSignAndSubmitChallenge RPCs) ====
	KeyPendingPairChallengeServerPubKeyInHex    string = "pendingPairChallenge:serverPubKeyInHex"
	KeyPendingPairChallengeReceivedAtUnixMillis string = "pendingPairChallenge:receivedAtUnixMillis"
	// ==== End of pending pair challenge ====
	// ==== Temporary keys for mock houston. Will remove soon ====
	KeyLastRandomPrivKeyInHex           string = "lastRandomPrivKeyInHex"
	KeySecurityCardUsageCount           string = "securityCardUsageCount"
	KeySecurityCardReplayCounter        string = "securityCardReplayCounter"
	KeySecretCardBytesInHex             string = "secretCardBytesInHex"
	KeySecurityCardPairingSlot          string = "securityCardPairingSlot"
	KeyTimeSinceLastChallengeUnixMillis string = "timeSinceLastChallengeUnixMillis"
	// ==== End of temporary keys for mock houston ====

	// ==== Lightning keys ====
	KeyLightningIncomingHTLCBatches string = "lightning:incomingHTLCBatches"
)

type ValueType interface {
	FromString(value string) (any, error)
	ToString(value any) (string, error)
}

type IntType struct{}
type LongType struct{}
type DoubleType struct{}
type StringType struct{}
type BoolType struct{}

func (IntType) FromString(value string) (any, error) {
	n, err := strconv.ParseInt(value, 10, 32)
	if err != nil {
		return nil, err
	}
	return int32(n), nil
}

func (IntType) ToString(value any) (string, error) {
	n, ok := value.(int32)
	if ok {
		return strconv.Itoa(int(n)), nil
	}
	return "", errors.Errorf("IntType: invalid type, expected int32")
}

func (LongType) FromString(value string) (any, error) {
	n, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return nil, err
	}
	return n, nil
}

func (LongType) ToString(value any) (string, error) {
	n, ok := value.(int64)
	if ok {
		return strconv.FormatInt(n, 10), nil
	}
	return "", errors.Errorf("LongType: invalid type, expected int64")
}

func (DoubleType) FromString(value string) (any, error) {
	f, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return nil, err
	}
	return f, nil
}

func (DoubleType) ToString(value any) (string, error) {
	f, ok := value.(float64)
	if ok {
		return strconv.FormatFloat(f, 'f', -1, 64), nil
	}
	return "", errors.Errorf("DoubleType: invalid type, expected float64")
}

func (StringType) FromString(value string) (any, error) {
	return value, nil
}

func (StringType) ToString(value any) (string, error) {
	str, ok := value.(string)
	if ok {
		return str, nil
	}
	return "", errors.Errorf("StringType: invalid type, expected string")
}

func (BoolType) FromString(value string) (any, error) {
	bl, err := strconv.ParseBool(value)
	if err != nil {
		return nil, err
	}
	return bl, nil
}

func (BoolType) ToString(value any) (string, error) {
	bo, ok := value.(bool)
	if ok {
		return strconv.FormatBool(bo), nil
	}
	return "", errors.Errorf("BoolType: invalid type, expected bool")
}

// Classification that should contain each stored value
type Classification struct {
	BackupType       BackupType
	BackupSecurity   BackupSecurity
	SecurityCritical bool
	ValueType        ValueType
}
