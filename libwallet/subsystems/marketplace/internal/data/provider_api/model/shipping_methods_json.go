package model

// Shipping methods a provider can answer with. A destination served both by
// mail and by courier answers ShippingMethodMailAndCourier.
const (
	ShippingMethodMail           = "mail"
	ShippingMethodCourier        = "courier"
	ShippingMethodMailAndCourier = "mail_and_courier"
)

// ShippingMethodsJSON is the response of GET /shipping_methods.
type ShippingMethodsJSON struct {
	ShippingMethod string `json:"shipping_method"`
}
