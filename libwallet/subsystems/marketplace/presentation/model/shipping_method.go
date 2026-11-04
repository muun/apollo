package model

// ShippingMethod is a way a provider can ship a card to the buyer.
//
// It's an int enum, not a string-typed one, to match the other marketplace
// enums such as card.Material and card.SecureElement.
type ShippingMethod int

const (
	ShippingMethodMail ShippingMethod = iota
	ShippingMethodCourier
)
