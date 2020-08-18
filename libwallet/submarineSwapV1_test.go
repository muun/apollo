package libwallet

import "testing"

func TestValidateSubmarineSwap(t *testing.T) {
	type args struct {
		rawInvoice    string
		userPublicKey string
		muunPublicKey string
		swap          SubmarineSwap
		network       *Network
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "succesful",
			args: args{
				rawInvoice:    "lnbcrt1p033394pp5sfcfh0ukkjfcvcg2vwk2hudue9d48lawqkacdan4msxne66w4krqdqqcqzpgsp5jelulm6a7q38j6jffa9qet3scz4qvcs08x6hfsyn0lfg34p2584q9qy9qsqjcq059jh8qeslj7qwl69ln69znalrxykhaj4kl0g0kfstwsa3warsyxx2d0rqs24tx896lz895wffqaj7l82zs896ec7r5arnw0cwtqpvzt8yd",
				userPublicKey: "tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk",
				muunPublicKey: "tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo",
				swap: &mockSubmarineSwap{
					fundingOutput: &mockSubmarineSwapFundingOutput{
						outputAddress:          "2MvW8nGkzFXnLWUca6ZGUh3yqEq5MKEyAxb",
						expirationInBlocks:     10,
						serverPaymentHashInHex: "82709bbf96b49386610a63acabf1bcc95b53ffae05bb86f675dc0d3ceb4ead86",
						userPublicKey:          "tpubD6NzVbkrYhZ4Y3iy9soFSA9zoYbpyhUFu3eAH1sDWyERxH2yJVZUhPUX5QsxD6bZfMWRKzxw28ohD5n6AZWmvZbDpZzgxSVxUnMevqzTXQk",
						muunPublicKey:          "tpubD6NzVbkrYhZ4XbhomyY2axxKe3KB1FK2Wq2z7XYyDF3T4QCuEDZFBUyGfjfHChvEbsbP9RpaYA8cwxkZpQjEcNdaPfuj3cKGqCiHC5YeRTo",
					},
					receiver: &mockSubmarineSwapReceiver{
						publicKey: "02c9a35bdbeab0b93ee9542d85c38beab7d1e72ea1d9639e5b00b1d5feb64bcfdd",
					},
				},
				network: Regtest(),
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			userPublicKey, _ := NewHDPublicKeyFromString(tt.args.userPublicKey, "m", Regtest())
			muunPublicKey, _ := NewHDPublicKeyFromString(tt.args.muunPublicKey, "m", Regtest())

			err := ValidateSubmarineSwapV1(
				tt.args.rawInvoice,
				userPublicKey,
				muunPublicKey,
				tt.args.swap,
				tt.args.network,
			)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateSubmarineSwapV1() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
