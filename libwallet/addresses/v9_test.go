package addresses

import (
	"reflect"
	"testing"
)

func TestCreateAddressV9(t *testing.T) {
	const (
		// Same keys and timelock as scanner/recovery_m3_test.go, which funds and sweeps the scheme
		// against bitcoind.
		userKey     = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		muunKey     = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		peerKey     = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath = "m/1/0"
		timelock    = int64(10)
		v9Address   = "bcrt1p3a9xkee8k99fsa7ffzqw6x3a7tcg4ycjzjd4hxecx563uhawnkjs88shdl"
	)

	user := derive(parseKey(userKey), "m", addressPath)
	muun := derive(parseKey(muunKey), "m", addressPath)
	peer := derive(parseKey(peerKey), "m", addressPath)

	got, err := CreateAddressV9(user, muun, peer, timelock, addressPath, network)
	if err != nil {
		t.Fatalf("CreateAddressV9() error = %v", err)
	}

	want := &WalletAddress{address: v9Address, derivationPath: addressPath, version: V9}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("CreateAddressV9() = %v, want %v", got, want)
	}
}
