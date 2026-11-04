package storage

//go:generate go run ../../../cmd/kv_migration_tool lock -migrations-file kv_migrations.go -lockfile testdata/kv_migrations.lock

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
		{"Exchange rate window", []Change{
			Define("exchangeRateWindow", NoAutoBackup, NotApplicable, false, &JSONType{}),
		}},
		{"Security card paired version", []Change{
			Define(
				"securityCardPairedVersion",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
		}},
		// The firmware calls this the call counter, so the key follows. The old
		// securityCardReplayCounter is abandoned rather than migrated: no V3
		// card is paired yet, so there is no counter worth carrying, and moving
		// it would need a custom change. A card that did hold a counter recovers
		// by pairing again, which zeroes its own.
		{"Mock Houston V3 call counter", []Change{
			Define("securityCardCallCounter", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
		// What the mock needs to verify the card's approval signature: the
		// commitment key it was paired with, and the reason hash it issued
		// with the outstanding challenge. Houston keeps both alongside the
		// card and the challenge.
		{"Mock Houston V3 card approval", []Change{
			Define(
				"securityCardCommitmentPubInHex",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
			Define(
				"lastChallengeReasonHashInHex",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
		}},
		{"Alarms registrations", []Change{
			Define("alarmRegistrations", NoAutoBackup, NotApplicable, false, &JSONType{}),
		}},
		// Each key caches one marketplace houston response as a JSON envelope
		// carrying the payload and when it was fetched.
		{"Security cards marketplace cache", []Change{
			Define(
				"securitycards:marketplace:cards",
				NoAutoBackup,
				NotApplicable,
				false,
				&JSONType{},
			),
			Define(
				"securitycards:marketplace:prices",
				NoAutoBackup,
				NotApplicable,
				false,
				&JSONType{},
			),
		}},
		// The short device id the card reported at SELECT, recorded next to its
		// version so the sign flow tells one card from another of the same
		// firmware. Empty for a firmware that reports none.
		{"Security card paired short device id", []Change{
			Define(
				"securityCardPairedShortDeviceId",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
		}},
		// The short device id of the card the mock paired, as its certificate
		// attests it, so the mock can hand it back with a challenge.
		{"Mock Houston V3 short device id", []Change{
			Define(
				"securityCardShortDeviceIdInHex",
				NoAutoBackup,
				NotApplicable,
				false,
				&StringType{},
			),
		}},
		// The control-plane blob the apps push, wrapped in an envelope
		// carrying when it was stored.
		{"Marketplace control plane", []Change{
			Define(
				"securitycards:marketplace:controlplane",
				NoAutoBackup,
				NotApplicable,
				false,
				&JSONType{},
			),
		}},
		{"Background Actions registrations", []Change{
			Define(
				"backgroundActionRegistrations",
				NoAutoBackup,
				NotApplicable,
				false,
				&JSONType{},
			),
		}},
	}
}
