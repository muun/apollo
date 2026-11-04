package nfc

// AppletVersionV3 mirrors the firmware GET_VERSION response (12 bytes:
// 6-byte vendor + 1-byte major + 1-byte minor + 4-byte commit hash).
type AppletVersionV3 struct {
	Vendor    string
	Major     byte
	Minor     byte
	GitCommit []byte
}
