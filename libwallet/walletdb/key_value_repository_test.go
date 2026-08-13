package walletdb

import (
	"strings"
	"testing"
	"time"
)

// TestKeyValueRepositoryTimestampsAreUTC verifies that every write method in
// keyValueRepository stores timestamps with a UTC timezone indicator ("Z").
// This guards against regressions where time.Now() without .UTC() would produce
// a local-timezone timestamp.
func TestKeyValueRepositoryTimestampsAreUTC(t *testing.T) {
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

	repo := db.NewKeyValueRepository()

	t.Run("Create", func(t *testing.T) {
		if err := repo.Create("create-key"); err != nil {
			t.Fatalf("Create failed: %v", err)
		}
		assertTimestampsUTC(t, db, "create-key")
	})

	t.Run("Save", func(t *testing.T) {
		value := "save-value"
		if err := repo.Save("save-key", &value); err != nil {
			t.Fatalf("Save failed: %v", err)
		}
		assertTimestampsUTC(t, db, "save-key")
	})

	t.Run("Update", func(t *testing.T) {
		// Seed the row first, then update it.
		initial := "initial"
		if err := repo.Save("update-key", &initial); err != nil {
			t.Fatalf("Save (seed) failed: %v", err)
		}
		if err := repo.Update("update-key", "new-value"); err != nil {
			t.Fatalf("Update failed: %v", err)
		}
		assertTimestampsUTC(t, db, "update-key")
	})

	t.Run("SaveBatch", func(t *testing.T) {
		v1, v2 := "v1", "v2"
		batch := map[string]*string{
			"batch-key-1": &v1,
			"batch-key-2": &v2,
		}
		if err := repo.SaveBatch(batch); err != nil {
			t.Fatalf("SaveBatch failed: %v", err)
		}
		assertTimestampsUTC(t, db, "batch-key-1")
		assertTimestampsUTC(t, db, "batch-key-2")
	})

	t.Run("UpdateAccordingToMap", func(t *testing.T) {
		old := "old-val"
		if err := repo.Save("map-key", &old); err != nil {
			t.Fatalf("Save (seed) failed: %v", err)
		}
		_, err := repo.UpdateAccordingToMap("map-key", map[string]string{"old-val": "new-val"})
		if err != nil {
			t.Fatalf("UpdateAccordingToMap failed: %v", err)
		}
		assertTimestampsUTC(t, db, "map-key")
	})
}

// assertTimestampsUTC queries the raw created_at and updated_at strings for the
// given key and fails the test if either lacks a UTC timezone marker.
func assertTimestampsUTC(t *testing.T, db *DB, key string) {
	t.Helper()

	rows, err := db.db.CommonDB().Query(
		`SELECT created_at, updated_at FROM key_values WHERE key = ?`, key,
	)
	if err != nil {
		t.Fatalf("query failed for key %q: %v", key, err)
	}
	defer rows.Close()

	if !rows.Next() {
		t.Fatalf("no row found for key %q", key)
	}

	var createdAt, updatedAt string
	if err := rows.Scan(&createdAt, &updatedAt); err != nil {
		t.Fatalf("scan failed for key %q: %v", key, err)
	}

	for _, ts := range []struct{ col, val string }{
		{"created_at", createdAt},
		{"updated_at", updatedAt},
	} {
		if !isUTC(ts.val) {
			t.Errorf("key %q: %s = %q does not contain UTC timezone indicator 'Z'",
				key, ts.col, ts.val)
		}
	}
}

// isUTC returns true if the raw timestamp string carries a UTC timezone marker.
func isUTC(ts string) bool {
	return strings.HasSuffix(ts, "Z")
}
