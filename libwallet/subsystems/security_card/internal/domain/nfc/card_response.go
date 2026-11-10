package nfc

type CardResponse struct {
	Response   []byte
	StatusCode uint16
}
