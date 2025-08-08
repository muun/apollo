package newop

import (
	"math"
	"path"
	"reflect"
	"testing"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/operation"
	"github.com/muun/libwallet/walletdb"
)

func TestDecodeFeeBumpFunctions(t *testing.T) {
	testCases := []struct {
		desc            string
		encodedFunction string
		expectedValues  [][]float64
		err             bool
	}{
		{
			desc:            "decode fee bump function with single interval",
			encodedFunction: "f4AAAD+AAAAAAAAA", //	[+Inf, 1, 0]
			expectedValues:  [][]float64{{math.Inf(1), 1, 0}},
			err:             false,
		},
		{
			desc:            "decode fee bump function with two intervals",
			encodedFunction: "QsgAAAAAAAAAAAAAf4AAAD+AAABAAAAA", //	[[100, 0, 0], [+Inf, 1, 2]]
			expectedValues: [][]float64{
				{100, 0, 0},
				{math.Inf(1), 1, 2},
			},
			err: false,
		},
		{
			desc:            "decode fee bump function incorrectly encoded (4 floats)",
			encodedFunction: "QEAAAAAAAAAAAAAAAAAAAA==", // [3 0 0 0]
			expectedValues:  nil,
			err:             true,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {
			feeBumpFunctionList, err := decodeFromBase64(tC.encodedFunction)

			if err != nil && !tC.err {
				t.Fatal(err)
			}

			if !reflect.DeepEqual(feeBumpFunctionList, tC.expectedValues) {
				t.Fatalf(
					"decoded values does not match expected, got: %+v, expected: %+v",
					feeBumpFunctionList,
					tC.expectedValues,
				)
			}
		})
	}
}

func TestPersistFeeBumpFunctions(t *testing.T) {
	testCases := []struct {
		desc                string
		encodedFunctionList []string
		expectedFunctions   []*operation.FeeBumpFunction
		err                 bool
	}{
		{
			desc:                "Persist single fee bump function",
			encodedFunctionList: []string{"QsgAAAAAAAAAAAAAf4AAAD+AAABAAAAA"}, // [[100, 0, 0], [+Inf, 1, 2]]
			expectedFunctions: []*operation.FeeBumpFunction{
				{
					PartialLinearFunctions: []*operation.PartialLinearFunction{
						{
							LeftClosedEndpoint: 0,
							RightOpenEndpoint:  100,
							Slope:              0,
							Intercept:          0,
						},
						{
							LeftClosedEndpoint: 100,
							RightOpenEndpoint:  math.Inf(1),
							Slope:              1,
							Intercept:          2,
						},
					},
				},
			},
			err: false,
		},
		{
			desc: "Persist with two fee bump functions",
			encodedFunctionList: []string{
				"QsgAAAAAAAAAAAAAf4AAAD+AAABAAAAA", // [[100, 0, 0], [+Inf, 1, 2]]
				"f4AAAD+AAAAAAAAA",                 // [[+Inf, 1, 0]]]
			},
			expectedFunctions: []*operation.FeeBumpFunction{
				{
					PartialLinearFunctions: []*operation.PartialLinearFunction{
						{
							LeftClosedEndpoint: 0,
							RightOpenEndpoint:  100,
							Slope:              0,
							Intercept:          0,
						},
						{
							LeftClosedEndpoint: 100,
							RightOpenEndpoint:  math.Inf(1),
							Slope:              1,
							Intercept:          2,
						},
					},
				},
				{
					PartialLinearFunctions: []*operation.PartialLinearFunction{
						{
							LeftClosedEndpoint: 0,
							RightOpenEndpoint:  math.Inf(1),
							Slope:              1,
							Intercept:          0,
						},
					},
				},
			},
			err: false,
		},
	}

	// Set temporary file for testing
	libwallet.Init(&app_provided_data.Config{DataDir: t.TempDir()})

	db, err := walletdb.Open(path.Join(libwallet.Cfg.DataDir, "wallet.db"))
	if err != nil {
		t.Fatalf("error opening DB")
	}
	defer db.Close()

	uuid := "uuid"
	refreshPolicy := "foreground"

	for _, tC := range testCases {
		functionList := libwallet.NewStringListWithElements(tC.encodedFunctionList)
		err := PersistFeeBumpFunctions(functionList, uuid, refreshPolicy)

		if err != nil && tC.err {
			t.Fatal(err)
		}

		repository := db.NewFeeBumpRepository()

		feeBumpFunctionSet, err := repository.GetAll()

		if err != nil {
			t.Fatalf("error getting bump functions")
		}

		if len(feeBumpFunctionSet.FeeBumpFunctions) != len(tC.expectedFunctions) ||
			feeBumpFunctionSet.RefreshPolicy != refreshPolicy ||
			feeBumpFunctionSet.UUID != uuid {
			t.Fatalf("fee bump functions were not saved properly")
		}

		for i, expectedFunction := range tC.expectedFunctions {
			if !reflect.DeepEqual(expectedFunction.PartialLinearFunctions, feeBumpFunctionSet.FeeBumpFunctions[i].PartialLinearFunctions) {
				t.Fatalf("fee bump functions were not saved properly")
			}
		}
	}
}
