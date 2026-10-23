package tlsdistrust

import (
	"github.com/go-errors/errors"
)

// ErrDistrustedCertificate is returned when every verified chain of the server goes through a
// distrusted certificate.
var ErrDistrustedCertificate = errors.New(
	"tlsdistrust: server certificate chains through a distrusted intermediate",
)
