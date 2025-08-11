package app_provided_data

type NfcBridge interface {
	Transmit(message []byte) (*NfcBridgeResponse, error)
}

type NfcBridgeResponse struct {
	Response   []byte
	StatusCode int32
}
