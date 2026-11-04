package newop

import rates "github.com/muun/libwallet/subsystems/rates/presentation"

// exchangeRates gives newop access to the rates subsystem. newop states are constructed by the
// apps through gomobile, so there is no construction path to inject it through.
var exchangeRates rates.Rates

// SetRates wires the rates subsystem into newop. Called by libwallet_init; gomobile skips it
// because its parameter type can't be bound.
func SetRates(r rates.Rates) {
	exchangeRates = r
}
