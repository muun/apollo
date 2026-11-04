package addresses

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcd/btcutil/hdkeychain"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
)

func TestCreateAddressV4(t *testing.T) {
	const (
		addressPath = "m/schema:1'/recovery:1'/external:1/2"

		v4Address       = "bcrt1qrs3vk4dzv70syck2qdz3g06tgckq4pftenuk5p77st9glnskpvtqe2tvvk"
		basePK          = "tpubDBf5wCeqg3KrLJiXaveDzD5JtFJ1ss9NVvFMx4RYS73SjwPEEawcAQ7V1B5DGM4gunWDeYNrnkc49sUaf7mS1wUKiJJQD6WEctExUQoLvrg" //nolint:lll
		baseCosigningPK = "tpubDB22PFkUaHoB7sgxh7exCivV5rAevVSzbB8WkFCCdbHq39r8xnYexiot4NGbi8PM6E1ySVeaHsoDeMYb6EMndpFrzVmuX8iQNExzwNpU61B" //nolint:lll
		basePath        = "m/schema:1'/recovery:1'"
	)

	baseCosignerKey := parseKey(baseCosigningPK)
	cosignerKey := derive(baseCosignerKey, basePath, addressPath)

	baseUserKey := parseKey(basePK)
	userKey := derive(baseUserKey, basePath, addressPath)

	type args struct {
		userKey     *hdkeychain.ExtendedKey
		cosignerKey *hdkeychain.ExtendedKey
	}
	tests := []struct {
		name    string
		args    args
		want    *walletaddresses.WalletAddress
		wantErr bool
	}{
		{name: "gen bech32 address",
			args: args{userKey: userKey, cosignerKey: cosignerKey},
			want: walletaddresses.New(V4, addressPath, v4Address)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := CreateAddressV4(tt.args.userKey, tt.args.cosignerKey, addressPath, network)
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
