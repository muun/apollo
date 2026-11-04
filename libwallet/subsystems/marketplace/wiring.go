package marketplace

import (
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/storage"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/subsystems/marketplace/internal/data"
	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/action"
	"github.com/muun/libwallet/subsystems/marketplace/internal/presentation"
)

type Marketplace = presentation.Marketplace

func NewMarketplace(
	houstonService houston.HoustonService,
	kvStorage *storage.KeyValueStorage,
	network *libwallet.Network,
) Marketplace {
	repository := data.NewMarketplaceRepository(houstonService, kvStorage)
	providerAPIRepository := data.NewProviderAPIRepository(
		data.NewProviderAPIConfigurator(data.BundledProviderAPIBaseURLs(network)),
	)

	return presentation.NewMarketplace(
		action.NewFetchAvailableCountriesAction(repository),
		action.NewFetchAvailableCountriesByProviderAction(repository),
		action.NewFetchCardOfferAction(repository),
		action.NewFetchProviderListingsByCountryAction(repository),
		action.NewFetchShippingMethodsAction(repository, providerAPIRepository),
		action.NewRefreshMarketplaceCardsAction(repository),
		action.NewRefreshMarketplacePricesAction(repository),
		action.NewSaveControlPlaneAction(repository),
	)
}
