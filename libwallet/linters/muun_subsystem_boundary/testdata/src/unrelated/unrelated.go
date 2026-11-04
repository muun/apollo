package unrelated

import "github.com/muun/libwallet/subsystems/beta/domain/model"

// Out of the analyzer's scope: this package is not under subsystems/, so reaching into a
// subsystem reports nothing.
var _ = model.Suffix
