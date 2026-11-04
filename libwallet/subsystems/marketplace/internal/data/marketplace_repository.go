// Package marketplace provides access to the security cards marketplace
// catalog, assembled from houston's cards and prices endpoints plus the
// provider metadata bundled in libwallet. The houston service is still the
// mock: wiring the real implementation is a separate task.
package data

import (
	"encoding/json"
	"log/slog"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

const (
	cardsCacheKey  = "securitycards:marketplace:cards"
	pricesCacheKey = "securitycards:marketplace:prices"

	cardsCacheTTL  = 60 * time.Minute
	pricesCacheTTL = 10 * time.Minute
)

// MarketplaceRepository provides the marketplace catalog and the countries
// where it is available, can force-refresh each cached houston endpoint, and
// owns the stored control plane the apps push.
type MarketplaceRepository interface { //nolint:revive // marketplace.MarketplaceRepository mirrors the rates repository naming
	Catalog() (*internal_model.Catalog, error)
	AvailableCountries() ([]pmodel.CountryInfo, error)
	RefreshCards() error
	RefreshPrices() error
	// StoreControlPlane persists the control plane, replacing the stored
	// one. Until the first store, reads fail closed: the
	// marketplace shows nothing.
	StoreControlPlane(controlPlane internal_model.ControlPlane) error
}

type houstonMarketplaceRepository struct {
	kv     *storage.KeyValueStorage
	now    func() time.Time
	cards  *cachedEndpoint[model.SecurityCardsMarketplaceCardsJSON]
	prices *cachedEndpoint[model.SecurityCardsMarketplacePricesJSON]
}

// NewMarketplaceRepository creates a marketplace repository backed by the
// (simulated) houston service, caching the cards and prices endpoints in the
// key-value storage, each with its own TTL. The available countries come
// from the control plane, read fresh on every call.
func NewMarketplaceRepository(
	houstonService houston.HoustonService,
	kv *storage.KeyValueStorage,
) MarketplaceRepository {
	return &houstonMarketplaceRepository{
		kv:  kv,
		now: time.Now,
		cards: newCachedEndpoint(
			kv,
			cardsCacheKey,
			cardsCacheTTL,
			time.Now,
			houstonService.FetchSecurityCardsMarketplaceCards,
		),
		prices: newCachedEndpoint(
			kv,
			pricesCacheKey,
			pricesCacheTTL,
			time.Now,
			houstonService.FetchSecurityCardsMarketplacePrices,
		),
	}
}

func (r *houstonMarketplaceRepository) Catalog() (
	*internal_model.Catalog,
	error,
) {
	cardsJSON, err := r.cards.get()
	if err != nil {
		return nil, errors.Errorf(
			"fetch marketplace cards: %w", err,
		)
	}
	pricesJSON, err := r.prices.get()
	if err != nil {
		return nil, errors.Errorf(
			"fetch marketplace prices: %w", err,
		)
	}

	return mapCatalog(
		cardsJSON,
		pricesJSON,
		bundledProvidersByUUID,
		r.storedControlPlane(),
	), nil
}

func (r *houstonMarketplaceRepository) RefreshCards() error {
	if err := r.cards.refresh(); err != nil {
		return errors.Errorf(
			"refresh marketplace cards: %w", err,
		)
	}
	return nil
}

func (r *houstonMarketplaceRepository) RefreshPrices() error {
	if err := r.prices.refresh(); err != nil {
		return errors.Errorf(
			"refresh marketplace prices: %w", err,
		)
	}
	return nil
}

func (r *houstonMarketplaceRepository) StoreControlPlane(
	controlPlane internal_model.ControlPlane,
) error {
	envelopeJSON, err := json.Marshal(controlPlaneEnvelope{
		SchemaVersion:      controlPlaneSchemaVersion,
		StoredAtUnixMillis: r.now().UnixMilli(),
		Payload:            mapControlPlaneToJSON(controlPlane),
	})
	if err != nil {
		return errors.Errorf("marshal control plane envelope: %w", err)
	}
	return r.kv.Save(controlPlaneKey, string(envelopeJSON))
}

func (r *houstonMarketplaceRepository) AvailableCountries() (
	[]pmodel.CountryInfo,
	error,
) {
	whitelisted := r.storedControlPlane().Countries
	countries := make([]pmodel.CountryInfo, 0, len(whitelisted))
	for _, code := range whitelisted {
		countries = append(countries, pmodel.NewCountryInfo(code))
	}
	return countries, nil
}

// storedControlPlane loads the stored control plane. Anything absent or
// unreadable is the closed control plane, never an error: the marketplace
// fails closed until the app's next push.
func (r *houstonMarketplaceRepository) storedControlPlane() internal_model.ControlPlane {
	value, err := r.kv.Get(controlPlaneKey)
	if err != nil {
		slog.Warn(
			"Failed to read the marketplace control plane; failing closed",
			slog.Any("error", err),
		)
		return internal_model.ControlPlane{}
	}
	if value == nil {
		// A legal off-state: the app hasn't pushed a control plane yet.
		return internal_model.ControlPlane{}
	}
	envelopeJSON, isString := value.(string)
	if !isString {
		slog.Warn(
			"Marketplace control plane holds a non-string value; failing closed",
		)
		return internal_model.ControlPlane{}
	}
	parsed, err := parseStoredControlPlane(envelopeJSON)
	if err != nil {
		slog.Warn(
			"Stored marketplace control plane is unreadable; failing closed",
			slog.Any("error", err),
		)
		return internal_model.ControlPlane{}
	}
	return parsed
}
