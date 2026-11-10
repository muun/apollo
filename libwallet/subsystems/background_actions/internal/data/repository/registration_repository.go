package repository

import (
	"context"
	"encoding/json"
	"maps"
	"slices"
	"sync"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

const registrationsKey = "backgroundActionRegistrations"

// RegistrationRepository stores the registry. Reads return only the registrations of known
// functions. The others stay stored untouched, for a build that knows them.
type RegistrationRepository interface {
	// Get returns the registration of fn, if any.
	Get(ctx context.Context, fn ba_model.Function) (*model.Registration, error)

	// List returns all registrations for known handlers, ordered by due time ascending.
	List(ctx context.Context) ([]*model.Registration, error)

	// Save writes the registration, replacing any other of its function.
	Save(ctx context.Context, registration *model.Registration) error

	// Delete removes the registration of fn, if any.
	Delete(ctx context.Context, fn ba_model.Function) error

	// UpdateIfSameGeneration writes the registration only when the generation hasn't changed,
	// and returns whether it did.
	UpdateIfSameGeneration(ctx context.Context, registration *model.Registration) (bool, error)

	// Wipe deletes all registrations.
	Wipe(ctx context.Context) error
}

// registrationRepository keeps the whole registry as one JSON array in the KV storage. The
// mutex makes each read-modify-write of the array atomic.
type registrationRepository struct {
	mutex          sync.Mutex
	kv             *storage.KeyValueStorage
	knownFunctions []ba_model.Function
}

// NewRegistrationRepository returns the registry of knownFunctions stored in kv.
func NewRegistrationRepository(
	kv *storage.KeyValueStorage,
	handlers map[ba_model.Function]ba_model.Handler,
) RegistrationRepository {
	return &registrationRepository{
		kv:             kv,
		knownFunctions: slices.Collect(maps.Keys(handlers)),
	}
}

func (r *registrationRepository) Get(
	ctx context.Context,
	fn ba_model.Function,
) (*model.Registration, error) {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	registrations, err := r.load(ctx)
	if err != nil {
		return nil, err
	}

	registration, ok := registrations[fn]
	if !ok {
		return nil, nil
	}

	return registration, nil
}

func (r *registrationRepository) List(ctx context.Context) ([]*model.Registration, error) {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	registrations, err := r.load(ctx)
	if err != nil {
		return nil, err
	}

	knownRegistrations := make([]*model.Registration, 0, len(registrations))
	for _, knownFunction := range r.knownFunctions {
		if registration, ok := registrations[knownFunction]; ok {
			knownRegistrations = append(knownRegistrations, registration)
		}
	}

	slices.SortStableFunc(knownRegistrations, func(a, b *model.Registration) int {
		return a.DueAt.Compare(b.DueAt)
	})

	return knownRegistrations, nil
}

func (r *registrationRepository) Save(ctx context.Context, registration *model.Registration) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	registrations, err := r.load(ctx)
	if err != nil {
		return err
	}

	registrations[registration.Function] = registration

	return r.store(ctx, registrations)
}

func (r *registrationRepository) Delete(ctx context.Context, fn ba_model.Function) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	registrations, err := r.load(ctx)
	if err != nil {
		return err
	}

	delete(registrations, fn)

	return r.store(ctx, registrations)
}

func (r *registrationRepository) UpdateIfSameGeneration(
	ctx context.Context,
	registration *model.Registration,
) (bool, error) {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	registrations, err := r.load(ctx)
	if err != nil {
		return false, err
	}

	storedRegistration, ok := registrations[registration.Function]
	if !ok || storedRegistration.Generation != registration.Generation {
		return false, nil
	}

	registrations[registration.Function] = registration
	if err := r.store(ctx, registrations); err != nil {
		return false, err
	}

	return true, nil
}

func (r *registrationRepository) Wipe(ctx context.Context) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if err := ctx.Err(); err != nil {
		return err
	}

	return r.kv.Delete(registrationsKey)
}

func (r *registrationRepository) load(
	ctx context.Context,
) (map[ba_model.Function]*model.Registration, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	value, err := r.kv.Get(registrationsKey)
	if err != nil {
		return nil, err
	}
	if value == nil {
		return map[ba_model.Function]*model.Registration{}, nil
	}

	var registrations map[ba_model.Function]*model.Registration
	if err := json.Unmarshal([]byte(value.(string)), &registrations); err != nil {
		return nil, errors.Errorf("decode registrations: %w", err)
	}

	return registrations, nil
}

func (r *registrationRepository) store(
	ctx context.Context,
	registrations map[ba_model.Function]*model.Registration,
) error {
	if err := ctx.Err(); err != nil {
		return err
	}

	encoded, err := json.Marshal(registrations)
	if err != nil {
		return errors.Errorf("encode registrations: %w", err)
	}

	return r.kv.Save(registrationsKey, string(encoded))
}
