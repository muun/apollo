package txscriptw

import (
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/btcsuitew/btcutilw"
)

// PayToAddrScript uses txscript.PayToAddrScript for all cases except AddressTaprootKey, which is
// by this wrapper.
func PayToAddrScript(address btcutil.Address) ([]byte, error) {
	// Detect the only additional case we support, delegate otherwise:
	trkAddr, ok := address.(*btcutilw.AddressTaprootKey)
	if !ok {
		return txscript.PayToAddrScript(address)
	}

	return payToTaprootKeyScript(trkAddr.ScriptAddress())
}

func payToTaprootKeyScript(key []byte) ([]byte, error) {
	return txscript.NewScriptBuilder().AddOp(txscript.OP_1).AddData(key).Script()
}
