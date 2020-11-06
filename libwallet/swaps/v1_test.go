package swaps

import (
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/muun/libwallet/addresses"
)

func TestValidateSubmarineSwapV1(t *testing.T) {
	type args struct {
		rawInvoice    string
		userPublicKey *KeyDescriptor
		muunPublicKey *KeyDescriptor
		swap          *SubmarineSwap
		network       *chaincfg.Params
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "successful",
			args: args{
				rawInvoice: "lnbcrt1p033394pp5sfcfh0ukkjfcvcg2vwk2hudue9d48lawqkacdan4msxne66w4krqdqqcqzpgsp5jelulm6a7q38j6jffa9qet3scz4qvcs08x6hfsyn0lfg34p2584q9qy9qsqjcq059jh8qeslj7qwl69ln69znalrxykhaj4kl0g0kfstwsa3warsyxx2d0rqs24tx896lz895wffqaj7l82zs896ec7r5arnw0cwtqpvzt8yd",
				userPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
					Path: "m",
				},
				muunPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					Path: "m",
				},
				swap: &SubmarineSwap{
					FundingOutput: SubmarineSwapFundingOutput{
						OutputAddress:          "2MvW8nGkzFXnLWUca6ZGUh3yqEq5MKEyAxb",
						ExpirationInBlocks:     10,
						ServerPaymentHashInHex: "82709bbf96b49386610a63acabf1bcc95b53ffae05bb86f675dc0d3ceb4ead86",
						UserRefundAddress:      addresses.New(addresses.V4, "m", "bcrt1q553urspdhwr49xavd67fvl35pzacz4853l4u09vntr8z06djnw7s95fgat"),
						UserPublicKey:          decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
						MuunPublicKey:          decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					},
					Receiver: SubmarineSwapReceiver{
						PublicKey: "02c9a35bdbeab0b93ee9542d85c38beab7d1e72ea1d9639e5b00b1d5feb64bcfdd",
					},
				},
				network: &chaincfg.RegressionNetParams,
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.args.swap.validateV1(
				tt.args.rawInvoice,
				tt.args.userPublicKey,
				tt.args.muunPublicKey,
				tt.args.network,
			)
			if (err != nil) != tt.wantErr {
				t.Errorf("validateV1() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func decodeKey(s string) *hdkeychain.ExtendedKey {
	key, err := hdkeychain.NewKeyFromString(s)
	if err != nil {
		panic(err)
	}
	return key
}
