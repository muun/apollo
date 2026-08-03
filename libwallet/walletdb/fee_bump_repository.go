package walletdb

import (
	"time"

	"github.com/go-errors/errors"
	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/sqlite"

	"github.com/muun/libwallet/operation"
)

type FeeBumpRepository interface {
	Store(feeBumpFunctionSet *operation.FeeBumpFunctionSet) error
	GetAll() (*operation.FeeBumpFunctionSet, error)
	GetCreationDate() (*time.Time, error)
	RemoveAll() error
}

type FeeBumpFunctionSet struct {
	gorm.Model
	UUID             string
	RefreshPolicy    string
	FeeBumpFunctions []FeeBumpFunction `gorm:"foreignKey:SetID"`
}

type FeeBumpFunction struct {
	gorm.Model
	Position uint
	// PartialLinearFunctions establishes a foreign key relationship with the PartialLinearFunction
	// table, where 'FunctionPosition' in PartialLinearFunction references 'Position' in
	// FeeBumpFunction.
	PartialLinearFunctions []PartialLinearFunction `gorm:"foreignKey:FunctionPosition;references:Position;"`                //nolint:lll
	SetID                  uint                    `                                                        sql:"not null"` //nolint:lll
}

type PartialLinearFunction struct {
	gorm.Model
	LeftClosedEndpoint float64
	RightOpenEndpoint  float64
	Slope              float64
	Intercept          float64
	FunctionPosition   uint
}

type feeBumpRepository struct {
	withDB gormProvider
}

func (r *feeBumpRepository) gorm(fn gormOperation) error {
	return r.withDB(fn)
}

func (r *feeBumpRepository) Store(feeBumpFunctionSet *operation.FeeBumpFunctionSet) error {
	dbFeeBumpFunctionSet := mapToDBFeeBumpFunctions(feeBumpFunctionSet)
	return r.gorm(func(db *gorm.DB) error {
		tx := db.Begin()
		if err := removeAllInTransaction(tx); err != nil {
			return errors.Errorf("error when trying to remove old fee bump functions: %w", err)
		}
		if err := tx.Create(&dbFeeBumpFunctionSet).Error; err != nil {
			tx.Rollback()
			return err
		}
		if err := tx.Commit().Error; err != nil {
			return errors.Errorf("failed to save fee bump functions: %w", err)
		}
		return nil
	})
}

func (r *feeBumpRepository) GetAll() (*operation.FeeBumpFunctionSet, error) {
	var result *operation.FeeBumpFunctionSet
	err := r.gorm(func(db *gorm.DB) error {
		var dbFeeBumpFunctionSet FeeBumpFunctionSet
		res := db.Preload("FeeBumpFunctions.PartialLinearFunctions").Find(&dbFeeBumpFunctionSet)
		if res.Error != nil && !gorm.IsRecordNotFoundError(res.Error) {
			return res.Error
		}
		result = mapToOperationFeeBumpFunctions(dbFeeBumpFunctionSet)
		return nil
	})
	return result, err
}

func (r *feeBumpRepository) GetCreationDate() (*time.Time, error) {
	var result *time.Time
	err := r.gorm(func(db *gorm.DB) error {
		var dbFeeBumpFunctionSet FeeBumpFunctionSet
		res := db.First(&dbFeeBumpFunctionSet)
		if res.Error != nil {
			return res.Error
		}
		result = &dbFeeBumpFunctionSet.CreatedAt
		return nil
	})
	return result, err
}

func (r *feeBumpRepository) RemoveAll() error {
	return r.gorm(func(db *gorm.DB) error {
		tx := db.Begin()
		if err := removeAllInTransaction(tx); err != nil {
			return err
		}
		return tx.Commit().Error
	})
}

func removeAllInTransaction(tx *gorm.DB) error {
	result := tx.Unscoped().Delete(FeeBumpFunctionSet{})
	if result.Error != nil {
		tx.Rollback()
		return result.Error
	}

	result = tx.Unscoped().Delete(FeeBumpFunction{})
	if result.Error != nil {
		tx.Rollback()
		return result.Error
	}

	result = tx.Unscoped().Delete(PartialLinearFunction{})
	if result.Error != nil {
		tx.Rollback()
		return result.Error
	}
	return nil
}

func mapToDBFeeBumpFunctions(feeBumpFunctionSet *operation.FeeBumpFunctionSet) FeeBumpFunctionSet {
	var dbFeeBumpFunctions []FeeBumpFunction
	for i, feeBumpFunction := range feeBumpFunctionSet.FeeBumpFunctions {
		var dbPartialLinearFunctions []PartialLinearFunction
		for _, partialLinearFunction := range feeBumpFunction.PartialLinearFunctions {
			dbPartialLinearFunctions = append(dbPartialLinearFunctions, PartialLinearFunction{
				LeftClosedEndpoint: partialLinearFunction.LeftClosedEndpoint,
				RightOpenEndpoint:  partialLinearFunction.RightOpenEndpoint,
				Slope:              partialLinearFunction.Slope,
				Intercept:          partialLinearFunction.Intercept,
				FunctionPosition:   uint(i),
			})
		}
		dbFeeBumpFunctions = append(dbFeeBumpFunctions, FeeBumpFunction{
			Position:               uint(i),
			PartialLinearFunctions: dbPartialLinearFunctions,
		})
	}

	return FeeBumpFunctionSet{
		UUID:             feeBumpFunctionSet.UUID,
		RefreshPolicy:    feeBumpFunctionSet.RefreshPolicy,
		FeeBumpFunctions: dbFeeBumpFunctions,
	}
}

func mapToOperationFeeBumpFunctions(
	dbFeeBumpFunctionSet FeeBumpFunctionSet,
) *operation.FeeBumpFunctionSet {
	dbFeeBumpFunctions := dbFeeBumpFunctionSet.FeeBumpFunctions
	var feeBumpFunctions []*operation.FeeBumpFunction
	for _, dbFeeBumpFunction := range dbFeeBumpFunctions {
		var partialLinearFunctions []*operation.PartialLinearFunction
		for _, dbPartialLinearFunction := range dbFeeBumpFunction.PartialLinearFunctions {
			partialLinearFunctions = append(
				partialLinearFunctions,
				&operation.PartialLinearFunction{
					LeftClosedEndpoint: dbPartialLinearFunction.LeftClosedEndpoint,
					RightOpenEndpoint:  dbPartialLinearFunction.RightOpenEndpoint,
					Slope:              dbPartialLinearFunction.Slope,
					Intercept:          dbPartialLinearFunction.Intercept,
				},
			)
		}

		feeBumpFunctions = append(
			feeBumpFunctions,
			&operation.FeeBumpFunction{
				PartialLinearFunctions: partialLinearFunctions,
			},
		)
	}

	return &operation.FeeBumpFunctionSet{
		CreatedAt:        dbFeeBumpFunctionSet.CreatedAt,
		UUID:             dbFeeBumpFunctionSet.UUID,
		RefreshPolicy:    dbFeeBumpFunctionSet.RefreshPolicy,
		FeeBumpFunctions: feeBumpFunctions,
	}
}
