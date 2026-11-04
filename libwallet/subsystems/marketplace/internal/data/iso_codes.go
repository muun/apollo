package data

import "regexp"

// Both predicates validate shape, not membership in the assigned ISO sets.
// Enumerating the assigned codes was considered and deliberately dropped:
// the lists drift (currencies get a new code every year or two — XCG 2025,
// ZWG 2024, SLE 2022 — and countries occasionally too), and a stale
// snapshot here would fail closed, dropping a provider over list trivia
// instead of data corruption. Codes that are well-shaped but unassigned
// are already absorbed downstream: the apps skip country codes their local
// ISO tables don't know, and a currency the rate window doesn't know fails
// at conversion time. If reviewers find shape too lax, membership
// validation is the follow-up.

// isIso3166Country reports whether code is shaped like an ISO 3166-1
// alpha-2 country code.
func isIso3166Country(code string) bool {
	return iso3166CountryRegex.MatchString(code)
}

// isIso4217Currency reports whether code is shaped like an ISO 4217
// currency code.
func isIso4217Currency(code string) bool {
	return iso4217CurrencyRegex.MatchString(code)
}

var iso3166CountryRegex = regexp.MustCompile(`^[A-Z]{2}$`)
var iso4217CurrencyRegex = regexp.MustCompile(`^[A-Z]{3}$`)
