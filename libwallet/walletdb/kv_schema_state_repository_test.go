package walletdb

import (
	"testing"
	"time"
)

// TestKVSchemaStateRepositoryTimestampsAreUTC verifies that BumpSchemaVersion
// stores applied_at with a UTC timezone indicator ("Z"). This guards against
// regressions where time.Now() without .UTC() would produce a local-timezone
// timestamp.
func TestKVSchemaStateRepositoryTimestampsAreUTC(t *testing.T) {
	loc, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		t.Skip("Cannot load timezone for test")
	}
	original := time.Local
	time.Local = loc
	defer func() { time.Local = original }()

	db, err := setupTestDb(t)
	if err != nil {
		t.Fatalf("failed to set up test db: %v", err)
	}
	defer db.Close()

	repo := db.NewKVSchemaStateRepository()

	if err := repo.BumpSchemaVersion(1); err != nil {
		t.Fatalf("BumpSchemaVersion failed: %v", err)
	}

	rows, err := db.db.CommonDB().Query(
		`SELECT applied_at FROM kv_schema_state WHERE schema_version = 1`,
	)
	if err != nil {
		t.Fatalf("query failed: %v", err)
	}
	defer rows.Close()

	if !rows.Next() {
		t.Fatal("no row found for schema_version 1")
	}

	var appliedAt string
	if err := rows.Scan(&appliedAt); err != nil {
		t.Fatalf("scan failed: %v", err)
	}

	if !isUTC(appliedAt) {
		t.Errorf("applied_at = %q does not contain UTC timezone indicator 'Z'", appliedAt)
	}
}
