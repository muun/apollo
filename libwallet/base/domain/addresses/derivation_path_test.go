package addresses

import (
	"testing"

	"github.com/stretchr/testify/require"
)

// The case set mirrors the backend's SchemaTest, so both implementations pin
// the same accept/reject table.
func TestValidateAddressDerivationPath(t *testing.T) {
	tests := []struct {
		name    string
		path    string
		wantErr bool
	}{
		{
			name:    "change branch",
			path:    "m/schema:1'/recovery:1'/change:0/0",
			wantErr: false,
		},
		{
			name:    "external branch",
			path:    "m/schema:1'/recovery:1'/external:1/7",
			wantErr: false,
		},
		{
			// Comments are cosmetic, so the canonical form is just as valid.
			name:    "canonical path",
			path:    "m/1'/1'/0/0",
			wantErr: false,
		},
		{
			name:    "contacts branch fails",
			path:    "m/schema:1'/recovery:1'/contacts:2/0",
			wantErr: true,
		},
		{
			name:    "metadata branch fails",
			path:    "m/schema:1'/recovery:1'/metadata:3/0",
			wantErr: true,
		},
		{
			name:    "missing index fails",
			path:    "m/schema:1'/recovery:1'/external:1",
			wantErr: true,
		},
		{
			name:    "extra depth fails",
			path:    "m/schema:1'/recovery:1'/external:1/0/0",
			wantErr: true,
		},
		{
			name:    "wrong base path fails",
			path:    "m/1/0",
			wantErr: true,
		},
		{
			name:    "hardened branch fails",
			path:    "m/schema:1'/recovery:1'/external:1'/0",
			wantErr: true,
		},
		{
			name:    "hardened index fails",
			path:    "m/schema:1'/recovery:1'/external:1/0'",
			wantErr: true,
		},
		{
			name:    "leading slash without m fails",
			path:    "/schema:1'/recovery:1'/external:1/0",
			wantErr: true,
		},
		{
			name:    "root-less path fails",
			path:    "schema:1'/recovery:1'/external:1/0",
			wantErr: true,
		},
		{
			name:    "malformed path fails",
			path:    "not a path",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := validateAddressDerivationPath(tt.path)
			if tt.wantErr {
				require.Error(t, err)
			} else {
				require.NoError(t, err)
			}
		})
	}
}
