package action

import (
	"log/slog"

	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// SaveControlPlaneAction persists the control plane the app pushed,
// replacing the stored one.
type SaveControlPlaneAction interface {
	// Run stores the control plane. Until the first store, the
	// marketplace fails closed and shows nothing.
	Run(controlPlane marketplace_model.ControlPlane) error
}

type saveControlPlaneAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewSaveControlPlaneAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) SaveControlPlaneAction {
	return &saveControlPlaneAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *saveControlPlaneAction) Run(
	controlPlane marketplace_model.ControlPlane,
) error {
	// A provider with more than one listings entry has conflicting country
	// lists, and either could be the intended one: report it and drop the
	// provider rather than guess. (A duplicated country code, by contrast,
	// is identical data, so the store just deduplicates it.)
	entries := make(map[string]int, len(controlPlane.Listings))
	for _, listing := range controlPlane.Listings {
		entries[listing.ProviderUUID]++
	}

	listings := make(map[string][]string, len(controlPlane.Listings))
	dropped := make(map[string]bool)
	for _, listing := range controlPlane.Listings {
		if entries[listing.ProviderUUID] > 1 {
			if !dropped[listing.ProviderUUID] {
				slog.Warn(
					"Skipping a provider with duplicated control plane listings",
					slog.String("providerUuid", listing.ProviderUUID),
				)
			}
			dropped[listing.ProviderUUID] = true
			continue
		}
		listings[listing.ProviderUUID] = listing.Countries
	}
	return ac.marketplaceRepository.StoreControlPlane(
		internal_model.NewControlPlane(
			controlPlane.Countries,
			listings,
			controlPlane.Cards,
		),
	)
}
