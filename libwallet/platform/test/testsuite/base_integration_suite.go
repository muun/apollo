package testsuite

import (
	"context"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

// Run takes a test suite and runs all the tests attached to it.
// This function is meant to run integration tests.
func Run(t *testing.T, s suite.TestingSuite) {
	if testing.Short() {
		t.Skip("skipping integration test suite")
	}
	suite.Run(t, s)
}

// BaseIntegrationSuite is the base suite to be used for integration test suites.
// It contains logic to be applied to all integration tests.
// Embed this struct in a concrete suite struct instead of suite.Suite.
type BaseIntegrationSuite struct {
	suite.Suite

	// Overrides testify default so that all failed assertions stop the test execution.
	*require.Assertions

	// Ctx is the per-test context to be used during tests.
	// Carries test configuration.
	Ctx context.Context
}

// SetupSuite is run automatically before all tests in the suite.
// If the concrete suite overrides this method, then it needs to be manually called.
func (s *BaseIntegrationSuite) SetupSuite() {
	s.Assertions = require.New(s.T())

	s.Ctx = s.T().Context()
}

// SetupTest is automatically run before each test in the suite.
// If the concrete suite overrides this method, then it needs to be manually called.
func (s *BaseIntegrationSuite) SetupTest() {
	s.Assertions = require.New(s.T())

	s.Ctx = s.T().Context()
}
