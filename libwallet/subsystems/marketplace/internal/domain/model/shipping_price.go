package model

import (
	"github.com/muun/libwallet/legacy/newop"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// ShippingPrice is one row of a provider's shipping table: the estimated
// price for a set of destination countries.
type ShippingPrice struct {
	Price     *newop.MonetaryAmount
	Countries []pmodel.CountryInfo
}

func NewShippingPrice(
	price *newop.MonetaryAmount,
	countries []pmodel.CountryInfo,
) ShippingPrice {
	return ShippingPrice{
		Price:     price,
		Countries: countries,
	}
}
