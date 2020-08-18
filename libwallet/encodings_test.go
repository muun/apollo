package libwallet

import (
	"encoding/hex"
	"math/big"
	"reflect"
	"testing"
)

func hexToBigInt(value string) *big.Int {
	result := &big.Int{}
	bytes, _ := hex.DecodeString(value)
	result.SetBytes(bytes)
	return result
}

func hexToBytes(value string) []byte {
	bytes, _ := hex.DecodeString(value)
	return bytes
}

func Test_paddedSerializeBigInt(t *testing.T) {

	type args struct {
		size uint
		x    *big.Int
	}
	tests := []struct {
		name string
		args args
		want []byte
	}{
		{
			name: "31 bytes key",
			args: args{size: 32, x: hexToBigInt("0e815b7892396a2e28e09c0d50082931eedd7fec16ef2e06724fe48f877ea6")},
			want: hexToBytes("000e815b7892396a2e28e09c0d50082931eedd7fec16ef2e06724fe48f877ea6"),
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := paddedSerializeBigInt(tt.args.size, tt.args.x); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("paddedSerializeBigInt() = %v, want %v", got, tt.want)
			}
		})
	}
}
