package libwallet

import (
	"reflect"
	"testing"
)

func Test_ripemd160(t *testing.T) {
	type args struct {
		data []byte
	}
	tests := []struct {
		name string
		args args
		want []byte
	}{
		{name: "simple",
			args: args{data: []byte{1, 2, 3}},
			want: []byte{121, 249, 1, 218, 38, 9, 240, 32, 173, 173, 191, 46, 95, 104, 161, 108, 140, 63, 125, 87}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := ripemd160(tt.args.data); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("ripemd160() = %v, want %v", got, tt.want)
			}
		})
	}

	t.Run("random input", func(t *testing.T) {
		small := randomBytes(8)
		smallRes := ripemd160(small)
		if smallRes == nil || len(smallRes) != 20 {
			t.Errorf("result is not of expected size for input (%v, %v)", smallRes, small)
		}

		big := randomBytes(120)
		bigRes := ripemd160(big)
		if bigRes == nil || len(bigRes) != 20 {
			t.Errorf("result is not of expected size for input (%v, %v)", bigRes, big)
		}
	})
}
