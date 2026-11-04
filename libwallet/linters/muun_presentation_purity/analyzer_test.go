package muun_presentation_purity

import (
	"testing"

	"golang.org/x/tools/go/analysis/analysistest"
)

func TestContract(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/alpha/presentation",
	)
}

func TestContractIsAnInterface(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/beta_gamma/presentation",
	)
}

func TestContractDeclaresTheInterface(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/delta/presentation",
	)
}

func TestImplementation(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/alpha/internal/presentation",
	)
}

func TestImplementationIsAStruct(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/beta_gamma/internal/presentation",
	)
}

func TestModel(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/alpha/presentation/model",
	)
}

func TestTests(t *testing.T) {
	testdata := analysistest.TestData()
	analysistest.Run(
		t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/alpha/presentation/tests",
	)
}
