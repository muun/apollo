package libwallet

import (
	"github.com/muun/libwallet/app_provided_data"
)

var Cfg *app_provided_data.Config

// Init configures the libwallet
func Init(c *app_provided_data.Config) {
	Cfg = c
}
