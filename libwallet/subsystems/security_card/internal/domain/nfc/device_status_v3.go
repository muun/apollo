package nfc

// DeviceStatusV3 is where a card sits in its configuration lifecycle. It is
// the device_status byte MetadataApplet reports in the metadata, with the
// applet's STATUS_* values. Each constant states its byte instead of taking
// it from its place in the list, so adding a status can never shift the
// others out of sync with the firmware.
type DeviceStatusV3 byte

const (
	// DeviceStatusUninitializedV3 is a card fresh from applet installation,
	// or partway through COMMIT_KEYS.
	DeviceStatusUninitializedV3 DeviceStatusV3 = 0x01
	// DeviceStatusMissingKeysV3 is a card that generated its own keys and
	// waits for CONFIGURE_KEYS to aggregate the vendor's.
	DeviceStatusMissingKeysV3 DeviceStatusV3 = 0x02
	// DeviceStatusMissingCertV3 is a card whose keys are final and waits for
	// CONFIGURE_CERTIFICATES to close the ceremony.
	DeviceStatusMissingCertV3 DeviceStatusV3 = 0x03
	// DeviceStatusReadyV3 is a card that completed the ceremony. It is the
	// only status in which the card pairs or answers a challenge.
	DeviceStatusReadyV3 DeviceStatusV3 = 0x04
	// DeviceStatusNukedV3 is a card that wiped its keys. The firmware
	// declares it, but no code path sets it yet.
	DeviceStatusNukedV3 DeviceStatusV3 = 0x05
)
