package action

import (
	"testing"

	// Test files may reach into another subsystem's non-presentation packages.
	betamodel "github.com/muun/libwallet/subsystems/beta/domain/model"
)

func TestSuffix(t *testing.T) {
	if betamodel.Suffix == "" {
		t.Fatal("empty suffix")
	}
}
