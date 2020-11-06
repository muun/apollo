package addresses

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcutil/hdkeychain"
)

func TestCreateAddressV3(t *testing.T) {

	const (
		addressPath = "m/schema:1'/recovery:1'/external:1/0"

		v3Address       = "2MswEXmCLaHQq6pUTtnUVF8wVArfYSqUec5"
		basePK          = "tpubDAN21T1DFREQQS4FvpUktKRBzXXsj5ddenAa5u198hLXvErFFR4Lj8bt8xMG3xnZr6u8mx1vrFW9RwCDXQwQuYRCLq1j9Nr2VJUrENzteQH"
		baseCosigningPK = "tpubDAsVhzq6otpasovieofhiaY38bSFGyJaBGvrJjBv9whhSnftUXfMTMVrq4BbTXT5A9b78CqqbPuM2j1ZGWdiggd7JHUTZAHh8GXDTt4Pkj9"
		basePath        = "m/schema:1'/recovery:1'"
		v3EncodedScript = "0020e1fbfbd395aff8b4087fee3e4488815ef659b559b3cd0d6800b5a591efd99f38"
	)

	baseMuunKey := parseKey(baseCosigningPK)
	muunKey := derive(baseMuunKey, basePath, addressPath)

	baseUserKey := parseKey(basePK)
	userKey := derive(baseUserKey, basePath, addressPath)

	type args struct {
		userKey *hdkeychain.ExtendedKey
		muunKey *hdkeychain.ExtendedKey
	}
	tests := []struct {
		name    string
		args    args
		want    *WalletAddress
		wantErr bool
	}{
		{name: "gen address",
			args: args{userKey: userKey, muunKey: muunKey},
			want: &WalletAddress{address: v3Address, derivationPath: addressPath, version: V3}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := CreateAddressV3(tt.args.userKey, tt.args.muunKey, addressPath, network)
			if (err != nil) != tt.wantErr {
				t.Errorf("CreateAddressV3() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateAddressV3() = %v, want %v", got, tt.want)
			}
		})
	}
}
