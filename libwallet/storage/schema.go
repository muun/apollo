package storage

import (
	"fmt"
	"strconv"
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

const (
	KeyIsBalanceHidden            string = "isBalanceHidden"
	KeyNightMode                  string = "nightMode"
	KeySecurityCardXpubSerialized string = "securityCardXpubSerialized"
	KeyBiometricsOptIn            string = "biometricsOptIn"
	KeyPinLength                  string = "pinLength"
	// TODO: These three are marked as prototypes to avoid accidentally setting the non-prototype fields
	//  in a consumer device before finalizing the design. Before production, the "Prototype" suffix must be removed
	UnverifiedEncryptedMuunKey string = "unverifiedEncryptedMuungKeyPrototype"
	VerifiedEncryptedMuunKey   string = "verifiedEncryptedMuunKeyPrototype"
	EncryptedUserKey           string = "encryptedUserKeyPrototype"

	// ==== Feature flag overrides ====
	FeatureFlagOverridesNfcCardV2Key = "featureFlagOverrides:nfcCardV2"
	// ==== End of feature flag overrides ====
	// ==== Temporary keys for mock houston. Will remove soon ====
	KeyLastRandomPrivKeyInHex           string = "lastRandomPrivKeyInHex"
	KeySecurityCardUsageCount           string = "securityCardUsageCount"
	KeySecretCardBytesInHex             string = "secretCardBytesInHex"
	KeySecurityCardPairingSlot          string = "securityCardPairingSlot"
	KeyTimeSinceLastChallengeUnixMillis string = "timeSinceLastChallengeUnixMillis"
	// ==== End of temporary keys for mock houston ====
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
	return "", fmt.Errorf("IntType: invalid type, expected int32")
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
	return "", fmt.Errorf("LongType: invalid type, expected int64")
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
	return "", fmt.Errorf("DoubleType: invalid type, expected float64")
}

func (StringType) FromString(value string) (any, error) {
	return value, nil
}

func (StringType) ToString(value any) (string, error) {
	str, ok := value.(string)
	if ok {
		return str, nil
	}
	return "", fmt.Errorf("StringType: invalid type, expected string")
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
	return "", fmt.Errorf("BoolType: invalid type, expected bool")
}

// Classification that should contain each stored value
type Classification struct {
	BackupType       BackupType
	BackupSecurity   BackupSecurity
	SecurityCritical bool
	ValueType        ValueType
}

func BuildStorageSchema() map[string]Classification {
	return map[string]Classification{
		KeyIsBalanceHidden: {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &BoolType{},
		},
		KeyNightMode: {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &StringType{},
		},
		KeySecurityCardXpubSerialized: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        &StringType{},
		},
                KeyBiometricsOptIn: {
                        BackupType:       NoAutoBackup,
                        BackupSecurity:   NotApplicable,
                        SecurityCritical: false,
                        ValueType:        &BoolType{},
                },
                KeyPinLength: {
                        BackupType:       NoAutoBackup,
                        BackupSecurity:   NotApplicable,
                        SecurityCritical: false,
                        ValueType:        &IntType{},
                },
		UnverifiedEncryptedMuunKey: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   Plain,
			SecurityCritical: false,
			ValueType:        &StringType{},
		},
		VerifiedEncryptedMuunKey: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   Authenticated,
			SecurityCritical: true,
			ValueType:        &StringType{},
		},
		EncryptedUserKey: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   Authenticated,
			SecurityCritical: true,
			ValueType:        &StringType{},
		},
		// ==== Feature flag overrides ====
		FeatureFlagOverridesNfcCardV2Key: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        &BoolType{},
		},
		// ==== End of feature flag overrides ====
		// ==== Temporary keys for mock houston. Will remove soon ====
		KeyLastRandomPrivKeyInHex: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        &StringType{},
		},
		KeySecurityCardUsageCount: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        IntType{},
		},
		KeySecretCardBytesInHex: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        StringType{},
		},
		KeySecurityCardPairingSlot: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        IntType{},
		},
		KeyTimeSinceLastChallengeUnixMillis: {
			BackupType:       AsyncAutoBackup,
			BackupSecurity:   NotApplicable,
			SecurityCritical: false,
			ValueType:        LongType{},
		},
		// ==== End of temporary keys for mock houston ====
	}
}
