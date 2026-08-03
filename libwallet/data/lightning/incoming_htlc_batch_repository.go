package lightning

import (
	"encoding/json"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/model/lightning"
	"github.com/muun/libwallet/storage"
)

// IncomingHTLCBatchRepository provides persistence operations for lightning HTLC batches.
type IncomingHTLCBatchRepository interface {
	Save(htlcBatch *lightning.IncomingHTLCBatch) error
	FindByID(htlcBatchID string) (*lightning.IncomingHTLCBatch, error)
	FindAll() (map[string]*lightning.IncomingHTLCBatch, error)
	DeleteByID(htlcBatchID string) error
}

type KvIncomingHTLCBatchRepository struct {
	kv *storage.KeyValueStorage
}

// NewIncomingHTLCBatchRepository creates a new HTLC repository backed by the given key-value
// storage.
func NewIncomingHTLCBatchRepository(kv *storage.KeyValueStorage) IncomingHTLCBatchRepository {
	return &KvIncomingHTLCBatchRepository{kv: kv}
}

func (r *KvIncomingHTLCBatchRepository) Save(htlcBatch *lightning.IncomingHTLCBatch) error {
	all, err := r.loadAll()
	if err != nil {
		return err
	}

	if htlcBatch.UUID == "" {
		return errors.Errorf("received htlcBatch should have its ID by now but it doesn't")
	}

	all[htlcBatch.UUID] = htlcBatch

	return r.saveAll(all)
}

func (r *KvIncomingHTLCBatchRepository) FindByID(
	htlcBatchID string,
) (*lightning.IncomingHTLCBatch, error) {
	all, err := r.loadAll()
	if err != nil {
		return nil, err
	}

	htlcBatch, ok := all[htlcBatchID]
	if !ok {
		return nil, nil
	}

	return htlcBatch, nil
}

func (r *KvIncomingHTLCBatchRepository) FindAll() (map[string]*lightning.IncomingHTLCBatch, error) {
	return r.loadAll()
}

func (r *KvIncomingHTLCBatchRepository) DeleteByID(htlcBatchID string) error {
	all, err := r.loadAll()
	if err != nil {
		return err
	}

	_, ok := all[htlcBatchID]
	if !ok {
		return nil
	}
	delete(all, htlcBatchID)

	return r.saveAll(all)
}

func (r *KvIncomingHTLCBatchRepository) loadAll() (map[string]*lightning.IncomingHTLCBatch, error) {
	value, err := r.kv.Get(storage.KeyLightningIncomingHTLCBatches)
	if err != nil {
		return nil, errors.Errorf("loadAll error when trying to retrieve KV storage key: %w", err)
	}

	if value == nil {
		return make(map[string]*lightning.IncomingHTLCBatch), nil
	}

	str, ok := value.(string)
	if !ok {
		return nil, errors.Errorf(
			"loadAll unexpected type for %s: %T",
			storage.KeyLightningIncomingHTLCBatches,
			value,
		)
	}

	var all map[string]*lightning.IncomingHTLCBatch
	if err := json.Unmarshal([]byte(str), &all); err != nil {
		return nil, errors.Errorf("loadAll error when unmarshalling HTLCs: %w", err)
	}

	return all, nil
}

func (r *KvIncomingHTLCBatchRepository) saveAll(all map[string]*lightning.IncomingHTLCBatch) error {
	data, err := json.Marshal(all)
	if err != nil {
		return errors.Errorf("saveAll error when marshalling HTLCs: %w", err)
	}

	return r.kv.Save(storage.KeyLightningIncomingHTLCBatches, string(data))
}
