package libwallet

import (
	"reflect"
	"testing"
)

func TestRecoveryCodeToKey(t *testing.T) {
	type args struct {
		code string
		salt string
	}
	tests := []struct {
		name    string
		args    args
		want    string
		wantErr bool
	}{
		{
			name:    "boop",
			args:    args{
				code: "3V4N-R9EC-V3TQ-NRB3-Q7NY-9HXP-CSDC-B5BC",
				salt: "63f701fda4fc0b0c",
			},
			want:    "0220af974bdfdf4274d21c061bbf21e67d3e2687f5b43073b4e9aac5bae53a3602",
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := RecoveryCodeToKey(tt.args.code, tt.args.salt)
			if (err != nil) != tt.wantErr {
				t.Errorf("RecoveryCodeToKey() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got.PubKeyHex(), tt.want) {
				t.Errorf("RecoveryCodeToKey() got = %v, want %v", got.PubKeyHex(), tt.want)
			}
		})
	}
}
