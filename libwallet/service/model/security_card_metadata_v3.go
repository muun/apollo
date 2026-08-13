package model

// SecurityCardV3MetadataJSON exposes the parsed V3 metadata. Field names
// mirror the firmware spec (attestation/capabilities/operationCount) so
// the same vocabulary travels from firmware → libwallet → Houston.
//
// Provider attestation cert fields (providerPubKey + providerSig) are
// part of the V3 protocol: the card stores them after STORE_CERTIFICATE
// and emits them with every GET_METADATA. providerSig is the ECDSA
// signature of attestationPubKey under providerPubKey.
type SecurityCardV3MetadataJSON struct {
	AttestationPubKeyInHex string `json:"attestationPubKeyInHex"`
	CardVendorInHex        string `json:"cardVendorInHex"`
	CardModelInHex         string `json:"cardModelInHex"`
	FirmwareVersion        uint16 `json:"firmwareVersion"`
	CapabilitiesInHex      string `json:"capabilitiesInHex"`
	OperationCount         uint16 `json:"operationCount"`
	ProviderPubKeyInHex    string `json:"providerPubKeyInHex"`
	ProviderSigInHex       string `json:"providerSigInHex"`
}
