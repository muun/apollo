package model

type CreateFirstSessionJson struct {
	Client          ClientJson    `json:"client"`
	GcmToken        *string       `json:"gcmToken,omitempty"`
	PrimaryCurrency string        `json:"primaryCurrency"`
	BasePublicKey   PublicKeyJson `json:"basePublicKey"`
}
