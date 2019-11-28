package libwallet

import (
	"reflect"
	"testing"
)

func Test_CreateAddressV2(t *testing.T) {

	const (
		addressPath   = "m/schema:1'/recovery:1'/external:1/0"
		originAddress = "2NDeWrsJEwvxwVnvtWzPjhDC5B2LYkFuX2s"

		encodedMuunKey  = "tpubDBYMnFoxYLdMBZThTk4uARTe4kGPeEYWdKcaEzaUxt1cesetnxtTqmAxVkzDRou51emWytommyLWcF91SdF5KecA6Ja8oHK1FF7d5U2hMxX"
		encodedUserKey  = "tprv8dfM4H5fYJirMai5Er3LguicgUAyxmcSQbFub5ens16amX1e1HAFiW4SXnFVw9nu9FedFQqTPGTTjPEmgfvvXMKww3UcRpFbbC4DFjbCcTb"
		basePath        = "m/schema:1'/recovery:1'"
		v2EncodedScript = "5221029fa5af7a34c142c1ce348b360abeb7de01df25b1d50129e58a67a6b846c9303b21025714f6b3670d4a38f5e2d6e8f239c9fc072543ce33dca54fcb4f4886a5cb87a652ae"
	)

	baseMuunKey, _ := NewHDPublicKeyFromString(encodedMuunKey, basePath, Regtest())
	muunKey, _ := baseMuunKey.DeriveTo(addressPath)

	baseUserKey, _ := NewHDPrivateKeyFromString(encodedUserKey, basePath, Regtest())
	userKey, _ := baseUserKey.DeriveTo(addressPath)

	type args struct {
		userKey *HDPublicKey
		muunKey *HDPublicKey
	}
	tests := []struct {
		name    string
		args    args
		want    MuunAddress
		wantErr bool
	}{
		{name: "gen address",
			args: args{userKey: userKey.PublicKey(), muunKey: muunKey},
			want: &muunAddress{address: originAddress, derivationPath: addressPath, version: addressV2}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := CreateAddressV2(tt.args.userKey, tt.args.muunKey)
			if (err != nil) != tt.wantErr {
				t.Errorf("CreateAddressV2() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateAddressV2() = %v, want %v", got, tt.want)
			}
		})
	}
}
