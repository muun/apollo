package model

type SetupChallengeResponseJson struct { //nolint:staticcheck // TODO: type SetupChallengeResponseJson should be SetupChallengeResponseJSON
	MuunKey            *string `json:"muunKey,omitempty"`
	MuunKeyFingerprint *string `json:"muunKeyFingerprint,omitempty"`
}
