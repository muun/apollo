package diagnostic_mode

import (
	"bytes"
	"fmt"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/electrum"
	"github.com/muun/libwallet/scanner"
	"log/slog"
)

type DiagnosticSessionData struct {
	Id       string
	DebugLog *bytes.Buffer
}

var diagnosticData = make(map[string]*DiagnosticSessionData)

func AddDiagnosticSession(data *DiagnosticSessionData) error {
	if _, ok := diagnosticData[data.Id]; ok {
		return fmt.Errorf("id %s already exists", data.Id)
	}

	diagnosticData[data.Id] = data
	return nil
}

func GetDiagnosticSession(id string) (*DiagnosticSessionData, bool) {
	result, ok := diagnosticData[id]
	return result, ok
}

func DeleteDiagnosticSession(id string) {
	delete(diagnosticData, id)
}

// ScanAddresses creates a stream of reports for the generated addresses
func ScanAddresses(keyProvider app_provided_data.KeyProvider, electrumProvider *electrum.ServerProvider, network *libwallet.Network, logger *slog.Logger) (<-chan *scanner.Report, error) {
	addresses, err := generateAddresses(keyProvider, network)
	if err != nil {
		return nil, err
	}

	const electrumPoolSize = 8
	connectionPool := electrum.NewPool(electrumPoolSize, true, logger)

	utxoScanner := scanner.NewScanner(connectionPool, electrumProvider, network.ToParams())

	return utxoScanner.Scan(addresses), nil
}

// generates a list of addresses to recover
func generateAddresses(keysProvider app_provided_data.KeyProvider, network *libwallet.Network) (chan libwallet.MuunAddress, error) {
	userKeyData, err := keysProvider.FetchUserKey()
	if err != nil {
		return nil, err
	}
	userPrivKey, err := libwallet.NewHDPrivateKeyFromString(userKeyData.Serialized, userKeyData.Path, network)
	if err != nil {
		return nil, err
	}
	userPubKey := userPrivKey.PublicKey()

	muunKeyData, err := keysProvider.FetchMuunKey()
	if err != nil {
		return nil, err
	}
	muunKey, err := libwallet.NewHDPublicKeyFromString(muunKeyData.Serialized, muunKeyData.Path, network)
	if err != nil {
		return nil, err
	}

	addrGen := scanner.NewAddressGenerator(userPubKey, muunKey, false)

	maxIndex := keysProvider.FetchMaxDerivedIndex()
	if maxIndex == 0 {
		return nil, fmt.Errorf("cannot generate 0 addresses")
	}
	addresses := addrGen.Stream(int64(maxIndex))

	return addresses, nil
}
