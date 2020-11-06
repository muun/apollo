package swaps

import (
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
)

func TestValidateSubmarineSwapV2(t *testing.T) {
	type args struct {
		rawInvoice                 string
		userPublicKey              *KeyDescriptor
		muunPublicKey              *KeyDescriptor
		swap                       *SubmarineSwap
		originalExpirationInBlocks int64
		network                    *chaincfg.Params
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "invalid invoice",
			args: args{
				swap: &SubmarineSwap{
					FundingOutput: SubmarineSwapFundingOutput{},
				},
				rawInvoice: "invalid",
				userPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
					Path: "m",
				},
				muunPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					Path: "m",
				},
				originalExpirationInBlocks: 0,
				network:                    &chaincfg.RegressionNetParams,
			},
			wantErr: true,
		},
		{
			name: "payment hash from server is invalid hex",
			args: args{
				swap: &SubmarineSwap{
					FundingOutput: SubmarineSwapFundingOutput{
						ServerPaymentHashInHex: "invalid hex",
					},
				},
				rawInvoice: "lnbcrt1p033394pp5sfcfh0ukkjfcvcg2vwk2hudue9d48lawqkacdan4msxne66w4krqdqqcqzpgsp5jelulm6a7q38j6jffa9qet3scz4qvcs08x6hfsyn0lfg34p2584q9qy9qsqjcq059jh8qeslj7qwl69ln69znalrxykhaj4kl0g0kfstwsa3warsyxx2d0rqs24tx896lz895wffqaj7l82zs896ec7r5arnw0cwtqpvzt8yd",
				userPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
					Path: "m",
				},
				muunPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					Path: "m",
				},
				originalExpirationInBlocks: 0,
				network:                    &chaincfg.RegressionNetParams,
			},
			wantErr: true,
		},
		{
			name: "payment hash from server does not match invoice",
			args: args{
				swap: &SubmarineSwap{
					FundingOutput: SubmarineSwapFundingOutput{
						ServerPaymentHashInHex: "112233445566778899",
					},
				},
				rawInvoice: "lnbcrt1p033394pp5sfcfh0ukkjfcvcg2vwk2hudue9d48lawqkacdan4msxne66w4krqdqqcqzpgsp5jelulm6a7q38j6jffa9qet3scz4qvcs08x6hfsyn0lfg34p2584q9qy9qsqjcq059jh8qeslj7qwl69ln69znalrxykhaj4kl0g0kfstwsa3warsyxx2d0rqs24tx896lz895wffqaj7l82zs896ec7r5arnw0cwtqpvzt8yd",
				userPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
					Path: "m",
				},
				muunPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					Path: "m",
				},
				originalExpirationInBlocks: 0,
				network:                    &chaincfg.RegressionNetParams,
			},
			wantErr: true,
		},
		// TODO: add more test cases for the different error conditions
		{
			name: "successful",
			args: args{
				swap: &SubmarineSwap{
					FundingOutput: SubmarineSwapFundingOutput{
						OutputAddress:          "bcrt1qk956axjf2pzmf6esd4jfrppkhmegn8eez2gl2zdkzje0w8lt2tmqvqvrut",
						ExpirationInBlocks:     10,
						ServerPaymentHashInHex: "82709bbf96b49386610a63acabf1bcc95b53ffae05bb86f675dc0d3ceb4ead86",
						UserPublicKey:          decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
						MuunPublicKey:          decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
						KeyPath:                "m",
					},
					Receiver: SubmarineSwapReceiver{
						PublicKey: "02c9a35bdbeab0b93ee9542d85c38beab7d1e72ea1d9639e5b00b1d5feb64bcfdd",
					},
				},
				rawInvoice: "lnbcrt1p033394pp5sfcfh0ukkjfcvcg2vwk2hudue9d48lawqkacdan4msxne66w4krqdqqcqzpgsp5jelulm6a7q38j6jffa9qet3scz4qvcs08x6hfsyn0lfg34p2584q9qy9qsqjcq059jh8qeslj7qwl69ln69znalrxykhaj4kl0g0kfstwsa3warsyxx2d0rqs24tx896lz895wffqaj7l82zs896ec7r5arnw0cwtqpvzt8yd",
				userPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk"),
					Path: "m",
				},
				muunPublicKey: &KeyDescriptor{
					Key:  decodeKey("tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo"),
					Path: "m",
				},
				originalExpirationInBlocks: 10,
				network:                    &chaincfg.RegressionNetParams,
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.args.swap.validateV2(
				tt.args.rawInvoice,
				tt.args.userPublicKey,
				tt.args.muunPublicKey,
				tt.args.originalExpirationInBlocks,
				tt.args.network,
			)
			if (err != nil) != tt.wantErr {
				t.Errorf("validateV2() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
