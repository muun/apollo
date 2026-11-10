// Package adapted provides Go-typed wrappers over the raw bridge
// interfaces in app_provided_data.
//
// The bridge interfaces use basic types (strings, byte slices, status
// codes) because gomobile can only bind a limited set of Go types.
// Adapters in this package deserialize and validate the raw data into
// the typed Go structs the rest of libwallet works with.
//
// Not every bridge interface has an adapter here. Candidates move in
// when the wrapper is pure type conversion with no business logic.
// Wrappers that add substantial behavior beyond deserialization (HTTP
// transport in base/data/houston, encryption in base/data/securekv, APDU
// protocol logic in security_card/nfc) stay in their owning package
// because the adapter and the logic are inseparable there.
package adapted
