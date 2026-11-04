package v2

// ServerFeatures models the structure of a `server.features` result.
type ServerFeatures struct {
	GenesisHash   string `json:"genesis_hash"`
	HashFunction  string `json:"hash_function"`
	ServerVersion string `json:"server_version"`
	ProtocolMin   string `json:"protocol_min"`
	ProtocolMax   string `json:"protocol_max"`
	Pruning       int    `json:"pruning"`
}

// GetHeadersResult models the structure of a `blockchain.headers.subscribe` result.
type GetHeadersResult struct {
	Height    int32  `json:"height"`
	HeaderHex string `json:"hex"`
}

// ScriptHashHistoryEntry models the structure of an item in a `blockchain.scripthash.get_history`
// result array.
type ScriptHashHistoryEntry struct {
	TxHash string `json:"tx_hash"`
	Height int32  `json:"height"`
}

// UnspentRef models the structure of an item of the array of a `blockchain.scripthash.listunspent`
// result.
type UnspentRef struct {
	TxHash string `json:"tx_hash"`
	TxPos  int    `json:"tx_pos"`
	Value  int64  `json:"value"`
	Height int    `json:"height"`
}
