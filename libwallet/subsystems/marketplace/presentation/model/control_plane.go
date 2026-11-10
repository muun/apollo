package model

// ControlPlane is the marketplace control plane the app pushes into
// libwallet: backend-driven whitelists, sourced from RealTimeData on the
// native side, that tune what the marketplace surfaces. Every lever fails
// closed — an empty lever hides everything it governs.
type ControlPlane struct {
	// Countries is the global shipping country whitelist (ISO 3166-1
	// alpha-2 codes).
	Countries []string
	// Listings holds one entry per active provider. A provider with no
	// entry is deactivated everywhere.
	Listings []ControlPlaneListing
	// Cards is the card UUID whitelist.
	Cards []string
}

func NewControlPlane(
	countries []string,
	listings []ControlPlaneListing,
	cards []string,
) ControlPlane {
	return ControlPlane{
		Countries: countries,
		Listings:  listings,
		Cards:     cards,
	}
}
