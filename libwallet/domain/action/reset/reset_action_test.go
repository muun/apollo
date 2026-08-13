package reset_test

import (
	"path"
	"sync"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/muun/libwallet/domain/action/reset"
	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"
)

func TestResetDataAction_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test")
	}
	suite.Run(t, new(resetDataActionSuite))
}

type resetDataActionSuite struct {
	suite.Suite
	*require.Assertions

	kvs    *storage.KeyValueStorage
	action reset.ResetDataAction
}

func (s *resetDataActionSuite) SetupTest() {
	s.Assertions = require.New(s.T())

	dbPath := path.Join(s.T().TempDir(), "test.db")
	var schema map[string]storage.Classification
	pool, err := walletdb.NewPool(dbPath, func(db *walletdb.DB) error {
		var migErr error
		schema, migErr = storage.RunKeyValueMigrations(db, storage.BuildKVMigrationPlan())
		return migErr
	})
	s.NoError(err)
	s.T().Cleanup(func() { pool.Close() })

	s.kvs = storage.NewKeyValueStorage(pool.NewKeyValueRepository(), schema)
	s.action = reset.NewResetDataAction(dbPath, pool, storage.BuildKVMigrationPlan())
}

func (s *resetDataActionSuite) Test_ResetClearsAndReopensDB() {
	s.NoError(s.kvs.Save(storage.KeyBiometricsOptIn, true))

	val, err := s.kvs.Get(storage.KeyBiometricsOptIn)
	s.NoError(err)
	s.Equal(true, val)

	s.NoError(s.action.Run())

	val, err = s.kvs.Get(storage.KeyBiometricsOptIn)
	s.NoError(err)
	s.Nil(val, "value should be cleared after reset")

	s.NoError(s.kvs.Save(storage.KeyBiometricsOptIn, false))
	val, err = s.kvs.Get(storage.KeyBiometricsOptIn)
	s.NoError(err)
	s.Equal(false, val)
}

func (s *resetDataActionSuite) Test_ConcurrentGetsDuringReset() {
	const goroutines = 20
	var wg sync.WaitGroup
	wg.Add(goroutines)

	for range goroutines {
		go func() {
			defer wg.Done()
			for range 50 {
				_, _ = s.kvs.Get(storage.KeyBiometricsOptIn)
			}
		}()
	}

	s.NoError(s.action.Run())
	wg.Wait()

	// Storage must still work after concurrent reset.
	s.NoError(s.kvs.Save(storage.KeyBiometricsOptIn, true))
	val, err := s.kvs.Get(storage.KeyBiometricsOptIn)
	s.NoError(err)
	s.Equal(true, val)
}
