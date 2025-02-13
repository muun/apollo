package walletdb

import (
	"math"
	"path"
	"reflect"
	"testing"
	"time"

	"github.com/muun/libwallet/operation"
)

func TestCreateFeeBumpFunctions(t *testing.T) {
	db, err := setupTestDb(t)
	if err != nil {
		t.Fatalf("failed to set up test db: %v", err)
	}
	defer db.Close()

	repository := db.NewFeeBumpRepository()

	expectedFeeBumpFunctionSet := &operation.FeeBumpFunctionSet{
		CreatedAt:     time.Now(),
		UUID:          "uuid1",
		RefreshPolicy: "foreground",
		FeeBumpFunctions: []*operation.FeeBumpFunction{
			{
				PartialLinearFunctions: []*operation.PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  300,
						Slope:              2,
						Intercept:          300,
					},
					{
						LeftClosedEndpoint: 300,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              3,
						Intercept:          200,
					},
				},
			},
			{
				PartialLinearFunctions: []*operation.PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  100,
						Slope:              2,
						Intercept:          100,
					},
					{
						LeftClosedEndpoint: 100,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              3,
						Intercept:          500,
					},
				},
			},
			{
				PartialLinearFunctions: []*operation.PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  1000,
						Slope:              2,
						Intercept:          1000,
					},
					{
						LeftClosedEndpoint: 1000,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              3,
						Intercept:          1500,
					},
				},
			},
		},
	}

	err = repository.Store(expectedFeeBumpFunctionSet)
	if err != nil {
		t.Fatalf("failed to save fee bump functions: %v", err)
	}

	loadedFeeBumpFunctionSet, err := repository.GetAll()
	if err != nil {
		t.Fatalf("failed to load fee bump functions: %v", err)
	}

	if loadedFeeBumpFunctionSet.UUID != expectedFeeBumpFunctionSet.UUID {
		t.Errorf("expected %v UUID, got %v", expectedFeeBumpFunctionSet.UUID, loadedFeeBumpFunctionSet.UUID)
	}

	if loadedFeeBumpFunctionSet.RefreshPolicy != expectedFeeBumpFunctionSet.RefreshPolicy {
		t.Errorf(
			"expected %v refresh policy, got %v",
			expectedFeeBumpFunctionSet.RefreshPolicy,
			loadedFeeBumpFunctionSet.RefreshPolicy,
		)
	}

	if len(loadedFeeBumpFunctionSet.FeeBumpFunctions) != len(expectedFeeBumpFunctionSet.FeeBumpFunctions) {
		t.Errorf(
			"expected %d fee bump functions, got %d",
			len(expectedFeeBumpFunctionSet.FeeBumpFunctions),
			len(loadedFeeBumpFunctionSet.FeeBumpFunctions))
	}

	expectedFeeBumpFunctions := expectedFeeBumpFunctionSet.FeeBumpFunctions
	for i, loadedFeeBumpFunction := range loadedFeeBumpFunctionSet.FeeBumpFunctions {
		if len(loadedFeeBumpFunction.PartialLinearFunctions) != len(expectedFeeBumpFunctions[i].PartialLinearFunctions) {
			t.Errorf(
				"expected %d intervals, got %d",
				len(expectedFeeBumpFunctions[i].PartialLinearFunctions),
				len(loadedFeeBumpFunction.PartialLinearFunctions),
			)
		}

		for j, loadedpartialLinearFunction := range loadedFeeBumpFunction.PartialLinearFunctions {
			expectedPartialLinearFunction := expectedFeeBumpFunctions[i].PartialLinearFunctions[j]

			if !reflect.DeepEqual(loadedpartialLinearFunction, expectedPartialLinearFunction) {
				t.Errorf("loaded and expected partial linear functions are not equal")
			}
		}
	}

	creationDate, err := repository.GetCreationDate()

	if err != nil {
		t.Fatalf("failed getting creation date: %v", err)
	}

	if loadedFeeBumpFunctionSet.CreatedAt != *creationDate {
		t.Fatalf("date mismatch: got: %v, expected: %v", *creationDate, loadedFeeBumpFunctionSet.CreatedAt)
	}

	err = repository.RemoveAll()
	if err != nil {
		t.Fatalf("failed removing all fee bump functions: %v", err)
	}

	loadedFeeBumpFunctionSet, err = repository.GetAll()
	if err != nil {
		t.Fatalf("failed to load fee bump functions: %v", err)
	}

	if len(loadedFeeBumpFunctionSet.FeeBumpFunctions) != 0 {
		t.Fatalf("fee bump functions were not removed")
	}

	var dbPartialLinearFunctions []PartialLinearFunction
	db.db.Find(&dbPartialLinearFunctions)
	if len(dbPartialLinearFunctions) != 0 {
		t.Fatalf("partial linear functions were not removed")
	}

	creationDate, err = repository.GetCreationDate()

	if err == nil {
		t.Fatalf("it should return an error because theare are not records")
	}

	if creationDate != nil {
		t.Fatalf("creation date should be null when there are not fee bump functions")
	}
}

func setupTestDb(t *testing.T) (*DB, error) {
	dir := t.TempDir()

	db, err := Open(path.Join(dir, "test.db"))
	if err != nil {
		return nil, err
	}

	return db, err
}
