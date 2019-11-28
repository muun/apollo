package libwallet

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
)

func TestMainnet(t *testing.T) {
	tests := []struct {
		name string
		want *Network
	}{
		{name: "Get", want: &Network{network: &chaincfg.MainNetParams}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Mainnet(); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Mainnet() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestTestnet(t *testing.T) {
	tests := []struct {
		name string
		want *Network
	}{
		{name: "Get", want: &Network{network: &chaincfg.TestNet3Params}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Testnet(); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Testnet() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestRegtest(t *testing.T) {
	tests := []struct {
		name string
		want *Network
	}{
		{name: "Get", want: &Network{network: &chaincfg.RegressionNetParams}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Regtest(); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Regtest() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestNetwork_Name(t *testing.T) {
	tests := []struct {
		name     string
		instance *Network
		want     string
	}{
		{name: "regtest", instance: Regtest(), want: "regtest"},
		{name: "mainnet", instance: Mainnet(), want: "mainnet"},
		{name: "testnet", instance: Testnet(), want: "testnet3"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.instance.Name(); got != tt.want {
				t.Errorf("Network.Name() = %v, want %v", got, tt.want)
			}
		})
	}
}
