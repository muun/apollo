# Libwallet

Local backend for the mobile apps (Android & iOS). Treat it like backend code.

## Conventions

The conventions for this codebase are documented in the review guidance, which is also what the
automated PR review uses. Read these before writing or reviewing libwallet code:

- **General Go**: `../.github/review/sources/go/review-go-style.md`: general Go style conventions that apply to all Go projects.
- **Libwallet**: `../.github/review/sources/go/review-libwallet-conventions.md`: Libwallet specific conventions.

The canonical upstream is the Notion "Go style-conventions guide" and "Libwallet conventions guide"
pages, cited from those files.

## KV Storage

New keys go in `BuildKVMigrationPlan()` (`storage/kv_migrations.go`) as an appended `Migration{...}` with `Define(...)` entries. `KeyValueStorage` rejects any key not in the migration-derived classification map, so a missing `Define` makes every save/get for that key fail with "classification not found" — and callers often swallow it silently.

- Never mutate a shipped migration — only append. Migrations are ordered history gated by the persisted schema version.
- After adding a migration, refresh the lockfile: `go generate ./storage/...`.
- `Define` currently only accepts `NoAutoBackup + NotApplicable + securityCritical=false`; other combinations panic until auto-backup and security-critical storage are implemented.
- `MigrateValueTypeWithMap`, `UpdateAccordingToMap`, and `AddCustomChange` need Wallet-team sign-off — rollback isn't designed yet.
- `storage/schema.go` holds the `ValueType` / `Classification` / `BackupType` machinery used by `Define`; its `KeyXxx` string constants are legacy — don't add new ones there. If the key is only referenced from client code, keep the string constant on the client side.

## Build & Test

Run all tests with:
```bash
cd libwallet && go test ./...
```

When modifying tests or logic covered by tests, run the affected tests:
```bash
go test ./path/to/affected/package/...
```

After applying any Go code changes, always run:
```bash
go vet ./...
go fmt ./...
```
