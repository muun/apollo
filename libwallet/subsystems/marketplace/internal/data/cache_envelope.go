package data

import (
	"encoding/json"
)

// cacheSchemaVersion stamps every stored envelope. json.Unmarshal ignores
// unknown fields and zeroes absent ones, so a payload written by an older
// build would otherwise decode "successfully" into a changed DTO with zeroed
// fields and be served as fresh. Bump this whenever the cards or prices DTO
// changes shape; a mismatch reads as a cache miss.
const cacheSchemaVersion = 1

// cacheEnvelope is the JSON document stored under each marketplace cache
// key: one houston response payload plus when it was fetched and the schema
// version it was written with.
type cacheEnvelope struct {
	SchemaVersion       int             `json:"schemaVersion"`
	FetchedAtUnixMillis int64           `json:"fetchedAtUnixMillis"`
	Payload             json.RawMessage `json:"payload"`
}
