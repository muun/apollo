package nfc

// mockMetadataAppletV3 is MetadataApplet, the persistent half of the V3
// firmware, as MockMuunCardV3 reaches it: what survives a MuunCardV3 upgrade
// lives there, and MuunCardV3 reaches it over SIO. The methods carry the SIO
// interface's names so the Go and the Java read side by side.
//
// Each firmware generation keeps its own applet: what it stores, how it lays
// the metadata out and what certificate it holds are the generation's, while
// the protocol MockMuunCardV3 runs on top is the same for all of them.
type mockMetadataAppletV3 interface {
	getDeviceStatus() DeviceStatusV3
	setDeviceStatus(status DeviceStatusV3)

	// getKeyAgreementKeyBytes returns the private half of key agreement key
	// keyIndex, the way the SIO call takes it. Firmware 3.0 holds one key.
	getKeyAgreementKeyBytes(keyIndex int) []byte

	// getCommitmentKeyBytes returns the private half of the key the card
	// signs its approvals with.
	getCommitmentKeyBytes() []byte

	// getShortDeviceID returns what MuunCardV3 reports at SELECT.
	getShortDeviceID() [ShortDeviceIDSizeV3]byte

	// getMetadataForAuth returns the form of the metadata the pairing MAC
	// covers, and getMetadataForTransmission the form GET_METADATA answers
	// and the pair response embeds, both describing key agreement key
	// keyIndex: the one PAIR used, or key 0 for GET_METADATA.
	getMetadataForAuth(keyIndex int) []byte
	getMetadataForTransmission(keyIndex int) []byte

	// getCertificate returns the certificate of key agreement key keyIndex.
	// It is the SIO interface's getCertificateLength and getCertificateChunk
	// in one call: the firmware reads a certificate in parts only because an
	// SIO call can write nothing but the APDU buffer. Firmware 3.0 holds one
	// certificate.
	getCertificate(keyIndex int) []byte

	// incrementChallengeCounter is the SIO call the card makes after
	// answering a challenge.
	//
	// NOTE: only firmware 3.0 has this call: its metadata reports how many
	// challenges the card answered, and 3.1's layout carries no such counter,
	// so its applet does nothing. The call is part of the interface because
	// MockMuunCardV3 runs the same protocol over every generation's applet.
	incrementChallengeCounter()

	// getPairingIndex returns the spec's last_pairing_index: the index of the
	// last pairing the card handed out, -1 before the first. PAIR checks it
	// against the budget and gives the new pairing the next index.
	//
	// NOTE: only firmware 3.1 counts pairings, with this call and
	// incrementPairingIndex. 3.0's PAIR_INDEX is a constant, so its applet
	// answers -1 and ignores the increment, and every pairing gets index 0.
	// Both calls are part of the interface for the same reason as
	// incrementChallengeCounter.
	getPairingIndex() int

	// incrementPairingIndex spends one pairing. The card makes the call
	// before it runs any key agreement, so the count bounds how many times
	// an attacker gets it to multiply its key agreement key by a point of
	// their choosing.
	incrementPairingIndex()

	// setFirmwareVersion is what MuunCardV3 pushes on its first SIO connect,
	// so the metadata reports it even after an upgrade replaces that applet.
	setFirmwareVersion(version [VersionSizeV3]byte)
}
