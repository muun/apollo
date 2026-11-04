package data

import (
	"encoding/json"
	"log/slog"

	"github.com/go-errors/errors"

	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
)

// controlPlaneKey is the KV storage key holding the stored control plane.
// Registered in the KV migration plan.
const controlPlaneKey = "securitycards:marketplace:controlplane"

// controlPlaneSchemaVersion stamps every stored control plane. The storage
// model is libwallet-owned (the apps push typed data over gRPC and never see
// this shape), so the version only guards reads across app upgrades that
// change it: a mismatch reads as a closed control plane until the next push.
// Bump it whenever controlPlaneJSON changes shape.
const controlPlaneSchemaVersion = 1

// controlPlaneEnvelope is the JSON document stored under the control-plane
// key: the control plane plus when it was stored, so a future staleness
// policy can be added without rewriting stored data.
type controlPlaneEnvelope struct {
	SchemaVersion      int              `json:"schemaVersion"`
	StoredAtUnixMillis int64            `json:"storedAtUnixMillis"`
	Payload            controlPlaneJSON `json:"payload"`
}

type controlPlaneJSON struct {
	Countries []string                  `json:"countries"`
	Listings  []controlPlaneListingJSON `json:"listings"`
	Cards     []string                  `json:"cards"`
}

type controlPlaneListingJSON struct {
	ProviderUUID string   `json:"providerUuid"`
	Countries    []string `json:"countries"`
}

// mapControlPlaneToJSON translates the control plane into the storage
// shape, dropping country codes that aren't ISO 3166-1 shaped: a malformed
// code would silently match no country, so it is reported and skipped rather
// than stored. One bad code must not reject the whole push — the rest of the
// control plane still applies.
func mapControlPlaneToJSON(
	in internal_model.ControlPlane,
) controlPlaneJSON {
	listings := make([]controlPlaneListingJSON, 0, len(in.Listings))
	for providerUUID, countries := range in.Listings {
		listings = append(listings, controlPlaneListingJSON{
			ProviderUUID: providerUUID,
			Countries:    validCountryCodes(countries),
		})
	}
	return controlPlaneJSON{
		Countries: validCountryCodes(in.Countries),
		Listings:  listings,
		Cards:     in.Cards,
	}
}

func mapJSONToControlPlane(
	in controlPlaneJSON,
) internal_model.ControlPlane {
	listings := make(map[string][]string, len(in.Listings))
	for _, listing := range in.Listings {
		listings[listing.ProviderUUID] = listing.Countries
	}
	return internal_model.NewControlPlane(
		in.Countries,
		listings,
		in.Cards,
	)
}

// validCountryCodes keeps the ISO 3166-1 shaped codes, deduplicated in
// first-seen order, reporting what it drops: a malformed code would
// silently match no country, and a duplicated one would surface twice in
// the lists built from it.
func validCountryCodes(codes []string) []string {
	seen := make(map[string]bool, len(codes))
	valid := make([]string, 0, len(codes))
	for _, code := range codes {
		if !isIso3166Country(code) {
			slog.Warn(
				"Skipping a malformed control plane country code",
				slog.String("countryCode", code),
			)
			continue
		}
		if seen[code] {
			slog.Warn(
				"Skipping a duplicated control plane country code",
				slog.String("countryCode", code),
			)
			continue
		}
		seen[code] = true
		valid = append(valid, code)
	}
	return valid
}

// parseStoredControlPlane decodes a stored envelope into a control plane.
// Any undecodable or version-mismatched state is an error: the caller fails
// closed.
func parseStoredControlPlane(
	envelopeJSON string,
) (internal_model.ControlPlane, error) {
	var envelope controlPlaneEnvelope
	if err := json.Unmarshal([]byte(envelopeJSON), &envelope); err != nil {
		return internal_model.ControlPlane{}, errors.Errorf(
			"unreadable control plane envelope: %w", err,
		)
	}
	// A control plane stored by a build with a different storage shape:
	// expected once across upgrades, closed until the next push.
	if envelope.SchemaVersion != controlPlaneSchemaVersion {
		return internal_model.ControlPlane{}, errors.Errorf(
			"unknown control plane schema version %d", envelope.SchemaVersion,
		)
	}
	return mapJSONToControlPlane(envelope.Payload), nil
}
