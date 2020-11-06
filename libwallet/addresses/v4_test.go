package addresses

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcutil/hdkeychain"
)

func TestCreateAddressV4(t *testing.T) {
	const (
		addressPath = "m/schema:1'/recovery:1'/external:1/2"

		v4Address       = "bcrt1qrs3vk4dzv70syck2qdz3g06tgckq4pftenuk5p77st9glnskpvtqe2tvvk"
		basePK          = "tpubDBf5wCeqg3KrLJiXaveDzD5JtFJ1ss9NVvFMx4RYS73SjwPEEawcAQ7V1B5DGM4gunWDeYNrnkc49sUaf7mS1wUKiJJQD6WEctExUQoLvrg"
		baseCosigningPK = "tpubDB22PFkUaHoB7sgxh7exCivV5rAevVSzbB8WkFCCdbHq39r8xnYexiot4NGbi8PM6E1ySVeaHsoDeMYb6EMndpFrzVmuX8iQNExzwNpU61B"
		basePath        = "m/schema:1'/recovery:1'"
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
		{name: "gen bech32 address",
			args: args{userKey: userKey, muunKey: muunKey},
			want: &WalletAddress{address: v4Address, derivationPath: addressPath, version: V4}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := CreateAddressV4(tt.args.userKey, tt.args.muunKey, addressPath, network)
			if (err != nil) != tt.wantErr {
				t.Errorf("CreateAddressV4() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateAddressV4() = %v, want %v", got, tt.want)
			}
		})
	}
}
