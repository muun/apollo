package model

type SignRequestChallengeResponseJSON struct {
	ServerPubKeyInHex string                            `json:"serverPubKeyInHex"`
	ReasonInHex       string                            `json:"reasonInHex"`
	PerCardPayloads   []SignChallengePerCardPayloadJSON `json:"perCardPayloads"`
}

// SignChallengePerCardPayloadJSON is the per-card slice of a sign
// challenge response. The client picks the tapped card's by matching the
// id it reported at SELECT against ShortDeviceIDInHex.
type SignChallengePerCardPayloadJSON struct {
	// ShortDeviceIDInHex is the card's id as its certificate attests it.
	// Empty for a firmware that reports none.
	//
	// NOTE: the spec names the card by its device id
	// (device_model_id || key_agreement_root[-16:]). The mock uses the short
	// device id because it already stores it; the production Houston should
	// use the device id.
	ShortDeviceIDInHex string `json:"shortDeviceIdInHex"`
	Index              uint16 `json:"index"`
	MacInHex           string `json:"macInHex"`
	CallCounter        uint32 `json:"callCounter"`
}
