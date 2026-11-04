// Package test holds the marketplace subsystem's test support.
package test

import (
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// PermissiveControlPlane whitelists every provider, card and shipping
// country of the simulated Houston fixtures, so the round-trip tests exercise
// the full dataset. The app-side dev stubs push data of the same shape but
// are free to serve whatever development data they like: the contract is
// the proto, not this fixture.
func PermissiveControlPlane() pmodel.ControlPlane {
	return pmodel.NewControlPlane(
		[]string{"US", "DE", "ES", "GB", "BR", "FR", "CA", "MX", "IT", "NL"},
		[]pmodel.ControlPlaneListing{
			pmodel.NewControlPlaneListing(
				"4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14",
				[]string{"US", "DE", "ES", "GB", "BR"},
			),
			pmodel.NewControlPlaneListing(
				"9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8",
				[]string{"US", "FR", "CA", "MX"},
			),
			pmodel.NewControlPlaneListing(
				"b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0",
				[]string{"DE", "IT", "NL"},
			),
		},
		[]string{
			"a1b2c3d4-1111-4000-8000-000000000001",
			"a1b2c3d4-1111-4000-8000-000000000002",
			"a1b2c3d4-1111-4000-8000-000000000003",
			"a1b2c3d4-1111-4000-8000-000000000004",
			"b2c3d4e5-2222-4000-8000-000000000001",
			"b2c3d4e5-2222-4000-8000-000000000002",
			"b2c3d4e5-2222-4000-8000-000000000003",
			"b2c3d4e5-2222-4000-8000-000000000004",
			"b2c3d4e5-2222-4000-8000-000000000005",
			"b2c3d4e5-2222-4000-8000-000000000006",
			"b2c3d4e5-2222-4000-8000-000000000007",
			"b2c3d4e5-2222-4000-8000-000000000008",
			"b2c3d4e5-2222-4000-8000-000000000009",
			"c3d4e5f6-3333-4000-8000-000000000001",
			"c3d4e5f6-3333-4000-8000-000000000002",
		},
	)
}
