package nfc

// PairingResponseV3 bundles what the server needs to verify a pairing:
// the card's ephemeral public key, the slot index, the metadata and the MAC.
//
// With extended APDUs the card returns everything in one response:
// P || index || metadata || MAC. The metadata is carried as the bytes the
// card emitted: its layout is the server's to read, since the server is who
// verifies the MAC over it and takes the card's keys out of it. The client
// relays it without looking inside, so a firmware that lays it out
// differently needs no change here.
type PairingResponseV3 struct {
	CardPublicKey []byte // P (65 bytes) - card's ephemeral public key
	Index         uint16 // 2 bytes
	Metadata      []byte // what the card reported about itself, as emitted
	MAC           []byte // 32 bytes - HMAC over tag || C || P || index || metadata
}
