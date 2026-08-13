package walletdb

import (
	"errors"
	"log"
	"time"

	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/sqlite"
	gormigrate "gopkg.in/gormigrate.v1"
)

type InvoiceState string

const (
	InvoiceStateRegistered InvoiceState = "registered"
	InvoiceStateUsed       InvoiceState = "used"
)

// TODO: probably rename to InvoiceSecrets or similar
type Invoice struct {
	gorm.Model
	Preimage      []byte
	PaymentHash   []byte
	PaymentSecret []byte
	KeyPath       string
	ShortChanId   uint64 //nolint:staticcheck // TODO: struct field ShortChanId should be ShortChanID
	AmountSat     int64
	State         InvoiceState
	Metadata      string
	UsedAt        *time.Time
}

type DB struct {
	db *gorm.DB
}

func open(path string) (*DB, error) {
	// _busy_timeout: retry for up to 1s before returning "database is locked" on concurrent writes.
	// Without it, SQLite fails immediately when two concurrent writes overlap. _journal_mode=WAL:
	// improves read/write concurrency. Readers can proceed concurrently with a writer, though
	// SQLite still allows only one writer at a time.
	db, err := gorm.Open("sqlite3", path+"?_busy_timeout=1000&_journal_mode=WAL")
	if err != nil {
		return nil, err
	}
	err = migrate(db)
	if err != nil {
		return nil, err
	}
	return &DB{db}, nil
}

func (d *DB) Gorm() *gorm.DB {
	return d.db
}

type gormOperation func(*gorm.DB) error

// gormProvider abstracts how a *gorm.DB is obtained for a repository
type gormProvider func(gormOperation) error

func (d *DB) NewFeeBumpRepository() FeeBumpRepository {
	return &feeBumpRepository{withDB: func(fn gormOperation) error { return fn(d.db) }}
}

func (d *DB) NewKeyValueRepository() KeyValueRepository {
	return &keyValueRepository{withDB: func(fn gormOperation) error { return fn(d.db) }}
}

func (d *DB) WithTx(fn func(*DB) error) error {
	tx := d.db.Begin()
	if tx.Error != nil {
		return tx.Error
	}
	if err := fn(&DB{db: tx}); err != nil {
		tx.Rollback()
		return err
	}
	return tx.Commit().Error
}

func (d *DB) NewKVSchemaStateRepository() KVSchemaStateRepository {
	return &kvSchemaStateRepository{withDB: func(fn gormOperation) error { return fn(d.db) }}
}

func migrate(db *gorm.DB) error {
	opts := gormigrate.Options{
		UseTransaction: true,
	}
	m := gormigrate.New(db, &opts, []*gormigrate.Migration{
		{
			ID: "initial",
			Migrate: func(tx *gorm.DB) error {
				type Invoice struct {
					gorm.Model
					Preimage      []byte
					PaymentHash   []byte
					PaymentSecret []byte
					KeyPath       string
					ShortChanId   uint64 //nolint:staticcheck // TODO: struct field ShortChanId should be ShortChanID
					State         string
					UsedAt        *time.Time
				}
				// This guard exists because at some point migrations were run outside a
				// transactional context and a user experimented problems with an invoices table
				// that was already created but whose migration had not been properly recorded.
				if !tx.HasTable(&Invoice{}) {
					return tx.CreateTable(&Invoice{}).Error
				}
				return nil
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.DropTable("invoices").Error
			},
		},
		{
			ID: "add amount to invoices table",
			Migrate: func(tx *gorm.DB) error {
				type Invoice struct {
					gorm.Model
					Preimage      []byte
					PaymentHash   []byte
					PaymentSecret []byte
					KeyPath       string
					ShortChanId   uint64 //nolint:staticcheck // TODO: struct field ShortChanId should be ShortChanID
					AmountSat     int64
					State         string
					UsedAt        *time.Time
				}
				return tx.AutoMigrate(&Invoice{}).Error
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.Table("invoices").DropColumn(gorm.ToColumnName("AmountSat")).Error
			},
		},
		{
			ID: "add metadata to invoices table",
			Migrate: func(tx *gorm.DB) error {
				type Invoice struct {
					gorm.Model
					Preimage      []byte
					PaymentHash   []byte
					PaymentSecret []byte
					KeyPath       string
					ShortChanId   uint64 //nolint:staticcheck // TODO: struct field ShortChanId should be ShortChanID
					AmountSat     int64
					State         InvoiceState
					Metadata      string
					UsedAt        *time.Time
				}
				return tx.AutoMigrate(&Invoice{}).Error
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.Table("invoices").DropColumn(gorm.ToColumnName("Metadata")).Error
			},
		},
		{
			ID: "Init fee bump tables",
			Migrate: func(tx *gorm.DB) error {

				type FeeBumpFunction struct {
					gorm.Model
					Position         uint
					FeeBumpIntervals []PartialLinearFunction `gorm:"foreignKey:FunctionPosition;references:Position;"` //nolint:lll
				}

				type PartialLinearFunction struct {
					gorm.Model
					LeftClosedEndpoint float64
					RightOpenEndpoint  float64
					Slope              float64
					Intercept          float64
					FunctionPosition   uint
				}
				// Create tables FeeBumpFunction and PartialLinearFunction
				return tx.AutoMigrate(&FeeBumpFunction{}, &PartialLinearFunction{}).Error
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.DropTable(&FeeBumpFunction{}, &PartialLinearFunction{}).Error
			},
		},
		{
			ID: "Add top level FeeBumpFunctionSet table and SetID field",
			Migrate: func(tx *gorm.DB) error {

				type FeeBumpFunctionSet struct {
					gorm.Model
					UUID             string
					RefreshPolicy    string
					FeeBumpFunctions []FeeBumpFunction `gorm:"foreignKey:SetID"`
				}

				type FeeBumpFunction struct {
					gorm.Model
					Position         uint
					FeeBumpIntervals []PartialLinearFunction `gorm:"foreignKey:FunctionPosition;references:Position;"` //nolint:lll
					SetID            uint                    `gorm:"default:0;not null"`
				}
				// Crea table FeeBumpFunctionSet and migrate FeeBumpFunction
				return tx.AutoMigrate(&FeeBumpFunctionSet{}, &FeeBumpFunction{}).Error
			},
			Rollback: func(tx *gorm.DB) error {

				if err := tx.DropTable(&FeeBumpFunctionSet{}).Error; err != nil {
					return err
				}

				col := gorm.ToColumnName("SetID")
				if err := tx.Table("fee_bump_functions").DropColumn(col).Error; err != nil {
					return err
				}

				return nil
			},
		},
		{
			ID: "Create key_values table",
			Migrate: func(tx *gorm.DB) error {

				type KeyValue struct {
					gorm.Model
					Key   string `gorm:"unique"`
					Value *string
				}

				return tx.AutoMigrate(&KeyValue{}).Error
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.DropTable("key_values").Error
			},
		},
		{
			ID: "create table kv_schema_state for key-value migrations",
			Migrate: func(tx *gorm.DB) error {
				// Note: this table was created with plain SQL instead of a struct like other
				// migrations. If a future migration needs to add columns via AutoMigrate,
				// consider that its equivalent struct representation is:
				//
				//   type KVSchemaState struct {
				//       SchemaVersion int       `gorm:"primaryKey"`
				//       AppliedAt     time.Time `gorm:"not null"`
				//   }
				//
				// GORM will diff the struct against the real table regardless
				// of how it was created.
				return tx.Exec(`
					CREATE TABLE IF NOT EXISTS kv_schema_state (
						schema_version INTEGER PRIMARY KEY,
						applied_at TIMESTAMP NOT NULL
					);
				`).Error
			},
			Rollback: func(tx *gorm.DB) error {
				return tx.Exec(`
					DROP TABLE IF EXISTS kv_schema_state;
				`).Error
			},
		},
	})
	return m.Migrate()
}

func (d *DB) CreateInvoice(invoice *Invoice) error {
	// uint64 values with high bit set are not supported, we will
	// have to convert back and forth
	invoice.ShortChanId = invoice.ShortChanId & 0x7FFFFFFFFFFFFFFF
	res := d.db.Create(invoice)
	invoice.ShortChanId = invoice.ShortChanId | (1 << 63)
	return res.Error
}

func (d *DB) SaveInvoice(invoice *Invoice) error {
	// uint64 values with high bit set are not supported, we will
	// have to convert back and forth
	invoice.ShortChanId = invoice.ShortChanId & 0x7FFFFFFFFFFFFFFF
	res := d.db.Save(invoice)
	invoice.ShortChanId = invoice.ShortChanId | (1 << 63)
	return res.Error
}

func (d *DB) FindFirstUnusedInvoice() (*Invoice, error) {
	var invoice Invoice
	filter := &Invoice{State: InvoiceStateRegistered}
	if res := d.db.Where(filter).First(&invoice); res.Error != nil {

		if errors.Is(res.Error, gorm.ErrRecordNotFound) {
			return nil, nil
		}

		return nil, res.Error
	}
	invoice.ShortChanId = invoice.ShortChanId | (1 << 63)
	return &invoice, nil
}

func (d *DB) CountUnusedInvoices() (int, error) {
	var count int
	filter := &Invoice{State: InvoiceStateRegistered}
	if res := d.db.Model(&Invoice{}).Where(filter).Count(&count); res.Error != nil {
		return 0, res.Error
	}
	return count, nil
}

func (d *DB) FindByPaymentHash(hash []byte) (*Invoice, error) {
	var invoice Invoice
	if res := d.db.Where(&Invoice{PaymentHash: hash}).First(&invoice); res.Error != nil {
		return nil, res.Error
	}
	invoice.ShortChanId = invoice.ShortChanId | (1 << 63)
	return &invoice, nil
}

func (d *DB) Close() {
	err := d.db.Close()
	if err != nil {
		log.Printf("error closing the db: %v", err)
	}
}
