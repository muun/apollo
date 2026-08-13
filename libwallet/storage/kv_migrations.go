package storage

//go:generate go run ../cmd/kv_migration_tool lock -migrations-file kv_migrations.go -lockfile testdata/kv_migrations.lock

// BuildKVMigrationPlan provides the ordered history of the key-value schema and data migrations.
func BuildKVMigrationPlan() []Migration {
	return []Migration{
		{"Initial schema", []Change{
			Define("isBalanceHidden", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{}),
			// TODO: migrate to AsyncAutoBackup, Authenticated
			Define("securityCardXpubSerialized", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("biometricsOptIn", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("pinLength", NoAutoBackup, NotApplicable, false, &IntType{}),
			// TODO: migrate to AsyncAutoBackup, Plain
			Define(
				"unverifiedEncryptedMuungKeyPrototype",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
			// TODO: migrate to AsyncAutoBackup, Authenticated, SecurityCritical
			Define(
				"verifiedEncryptedMuunKeyPrototype",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
			// TODO: migrate to AsyncAutoBackup, Authenticated, SecurityCritical
			Define("encryptedUserKeyPrototype", NoAutoBackup, NotApplicable, false, &StringType{}),
			// TODO: migrate to AsyncAutoBackup, Plain
			Define(
				"featureFlagOverrides:nfcCardV2",
				NoAutoBackup,
				NotApplicable,
				false,
				&BoolType{},
			),
			// TODO: migrate to AsyncAutoBackup, Plain
			Define(
				"featureFlagOverrides:ekGoRendering",
				NoAutoBackup,
				NotApplicable,
				false,
				&BoolType{},
			),
		}},
		{"Mock Houston server state", []Change{
			Define("lastRandomPrivKeyInHex", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("securityCardUsageCount", NoAutoBackup, NotApplicable, false, &IntType{}),
			Define("secretCardBytesInHex", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("securityCardPairingSlot", NoAutoBackup, NotApplicable, false, &IntType{}),
			Define(
				"timeSinceLastChallengeUnixMillis",
				NoAutoBackup,
				NotApplicable,
				false,
				&LongType{},
			),
		}},
		{"Pending pair challenge", []Change{
			// TODO: migrate to SecurityCritical
			Define(
				"pendingPairChallenge:serverPubKeyInHex",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
			Define(
				"pendingPairChallenge:receivedAtUnixMillis",
				NoAutoBackup,
				NotApplicable,
				false,
				&LongType{},
			),
		}},
		{"Mock Houston V3 replay counter", []Change{
			Define("securityCardReplayCounter", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
		{"NFC empirical Extended APDU signals", []Change{
			Define(
				"nfc_extended_apdu_supported",
				NoAutoBackup,
				NotApplicable,
				false,
				&BoolType{},
			),
			Define(
				"nfc_max_transceive_length",
				NoAutoBackup,
				NotApplicable,
				false,
				&IntType{},
			),
		}},
	}
}
