package model

// SignRequestChallengeJSON is the V3 sign challenge request. The client
// asks Houston to issue a challenge that the card will sign to approve
// a sensitive action. Action identifies what is being approved — it is
// a discriminated union (see ActionDescriptor) carrying a "type" tag
// alongside the variant-specific fields.
type SignRequestChallengeJSON struct {
	Action ActionDescriptor `json:"action"`
}
