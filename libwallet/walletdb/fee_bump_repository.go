package walletdb

import (
	"fmt"
	"time"

	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/sqlite"
	"github.com/muun/libwallet/operation"
)

type FeeBumpRepository interface {
	Store(feeBumpFunctions []*operation.FeeBumpFunction) error
	GetAll() ([]*operation.FeeBumpFunction, error)
	GetCreationDate() (*time.Time, error)
	RemoveAll() error
}

type FeeBumpFunction struct {
	gorm.Model
	Position uint
	// PartialLinearFunctions establishes a foreign key relationship with the PartialLinearFunction table,
	// where 'FunctionPosition' in PartialLinearFunction references 'Position' in FeeBumpFunction.
	PartialLinearFunctions []PartialLinearFunction `gorm:"foreignKey:FunctionPosition;references:Position;"`
}

type PartialLinearFunction struct {
	gorm.Model
	LeftClosedEndpoint float64
	RightOpenEndpoint  float64
	Slope              float64
	Intercept          float64
	FunctionPosition   uint
}

type GORMFeeBumpRepository struct {
	db *gorm.DB
}

func (r *GORMFeeBumpRepository) Store(feeBumpFunctions []*operation.FeeBumpFunction) error {
	dbFeeBumpFunctions := mapToDBFeeBumpFunctions(feeBumpFunctions)

	tx := r.db.Begin()

	// Remove old data before store updated functions
	err := removeAllInTransaction(tx)
	if err != nil {
		return fmt.Errorf("error when trying to remove old fee bump functions: %w", err)
	}

	for _, feeBumpFunction := range dbFeeBumpFunctions {
		if err := tx.Create(&feeBumpFunction).Error; err != nil {
			tx.Rollback()
			return err
		}
	}

	if err := tx.Commit().Error; err != nil {
		return fmt.Errorf("failed to save fee bump functions: %w", err)
	}
	return nil
}

func (r *GORMFeeBumpRepository) GetAll() ([]*operation.FeeBumpFunction, error) {
	var dbFeeBumpFunctions []FeeBumpFunction

	result := r.db.Preload("PartialLinearFunctions").Order("position asc").Find(&dbFeeBumpFunctions)

	if result.Error != nil {
		return nil, result.Error
	}

	feeBumpFunctions := mapToOperationFeeBumpFunctions(dbFeeBumpFunctions)

	return feeBumpFunctions, nil
}

func (r *GORMFeeBumpRepository) GetCreationDate() (*time.Time, error) {
	var dbFeeBumpFunction FeeBumpFunction
	result := r.db.First(&dbFeeBumpFunction)

	if result.Error != nil {
		return nil, result.Error
	}

	return &dbFeeBumpFunction.CreatedAt, nil
}

func (r *GORMFeeBumpRepository) RemoveAll() error {
	tx := r.db.Begin()
	err := removeAllInTransaction(tx)
	if err != nil {
		return err
	}

	return tx.Commit().Error
}

func removeAllInTransaction(tx *gorm.DB) error {
	result := tx.Delete(FeeBumpFunction{})
	if result.Error != nil {
		tx.Rollback()
		return result.Error
	}

	result = tx.Delete(PartialLinearFunction{})
	if result.Error != nil {
		tx.Rollback()
		return result.Error
	}
	return nil
}

func mapToDBFeeBumpFunctions(feeBumpFunctions []*operation.FeeBumpFunction) []FeeBumpFunction {
	var dbFeeBumpFunctions []FeeBumpFunction
	for i, feeBumpFunction := range feeBumpFunctions {
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

	return dbFeeBumpFunctions
}

func mapToOperationFeeBumpFunctions(dbFeeBumpFunctions []FeeBumpFunction) []*operation.FeeBumpFunction {
	var feeBumpFunctions []*operation.FeeBumpFunction
	for _, dbFeeBumpFunction := range dbFeeBumpFunctions {
		var partialLinearFunctions []*operation.PartialLinearFunction
		for _, dbPartialLinearFunction := range dbFeeBumpFunction.PartialLinearFunctions {
			partialLinearFunctions = append(partialLinearFunctions, &operation.PartialLinearFunction{
				LeftClosedEndpoint: dbPartialLinearFunction.LeftClosedEndpoint,
				RightOpenEndpoint:  dbPartialLinearFunction.RightOpenEndpoint,
				Slope:              dbPartialLinearFunction.Slope,
				Intercept:          dbPartialLinearFunction.Intercept,
			})
		}

		feeBumpFunctions = append(
			feeBumpFunctions,
			&operation.FeeBumpFunction{
				CreatedAt:              dbFeeBumpFunction.CreatedAt,
				PartialLinearFunctions: partialLinearFunctions,
			},
		)
	}
	return feeBumpFunctions
}
