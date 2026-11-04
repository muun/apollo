package model

// SecurityCardSigned is what a sign leaves the native layer to report: the firmware version the
// card that signed named itself with, as major.minor ("3.1").
type SecurityCardSigned struct {
	FirmwareVersion string
}

// NewSecurityCardSigned returns what a sign leaves to report for a card of firmwareVersion.
func NewSecurityCardSigned(firmwareVersion string) *SecurityCardSigned {
	return &SecurityCardSigned{
		FirmwareVersion: firmwareVersion,
	}
}
