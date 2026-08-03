package v2

import (
	"context"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/observability/slogctx"
)

const (
	clientName    = "muun"
	clientVersion = "1.4" // Electrum version we support.
)

type ConnectionType int

const (
	RequireTLS ConnectionType = iota
	RequireTCP
	PreferTLS
)

// Client is a TLS client that implements a subset of the Electrum protocol.
//
// It includes a minimal implementation of a JSON-RPC client, since the one provided by the
// standard library doesn't support features such as batching.
//
// It is not thread-safe for concurrent calls. Every Client should have a single owner.
type Client interface {
	GetServerAddress() string
	GetServerImplementation() string
	GetProtocolVersion() string

	Disconnect(ctx context.Context)
	IsConnected() bool

	ServerFeatures(ctx context.Context) (ServerFeatures, error)
	ServerPeers(ctx context.Context) ([]string, error)

	Broadcast(ctx context.Context, rawTx string) (string, error)
	BroadcastBatch(ctx context.Context, rawTxs []string) (map[string]Result[string], error)

	GetBestBlockHeight(ctx context.Context) (int, error)
	GetHeaders(ctx context.Context) (GetHeadersResult, error)

	GetScriptHashHistory(ctx context.Context, scriptHash string) ([]ScriptHashHistoryEntry, error)
	GetScriptHashHistoryBatch(
		ctx context.Context,
		scriptHashes []string,
	) (map[string]Result[[]ScriptHashHistoryEntry], error)

	GetTransaction(ctx context.Context, txID string) (string, error)
	GetTransactionBatch(ctx context.Context, txIDs []string) (map[string]Result[string], error)

	ListUnspent(ctx context.Context, indexHash string) ([]UnspentRef, error)
	ListUnspentBatch(
		ctx context.Context,
		indexHashes []string,
	) (map[string]Result[[]UnspentRef], error)
}

type client struct {
	serverAddress        string
	serverImplementation string
	protocolVersion      string
	rpcClient            *rpcClient
}

// NewClient creates an initialized Client instance and connects to the Electrum server.
func NewClient(ctx context.Context, serverAddress string, connType ConnectionType) (Client, error) {
	// Connect to Electrum server
	rpcClient, err := newRPCClient(ctx, serverAddress, connType)
	if err != nil {
		slogctx.Error(ctx, "Failed to establish connection to Electrum server",
			"server_address", serverAddress,
			"connection_type", connType,
			"error", err,
		)
		return nil, err
	}

	client := &client{
		serverAddress: serverAddress,
		rpcClient:     rpcClient,
	}

	// Upon connection, we MUST identify server
	if err := client.identifyServer(ctx); err != nil {
		client.Disconnect(ctx)
		slogctx.Error(ctx, "Failed to identify Electrum server",
			"server_address", serverAddress,
			"connection_type", connType,
			"error", err,
		)
		return nil, errors.Errorf("identify server: %w", err)
	}

	slogctx.Info(ctx, "Successfully connected to Electrum server",
		"server_address", client.serverAddress,
		"server_implementation", client.serverImplementation,
		"protocol_version", client.protocolVersion,
	)

	return client, nil
}

// identifyServer calls the `server.version` method and saves the returned
// [server implementation, protocol version] tuple.
// `server.version` MUST be called upon connection, and cannot be called again.
func (c *client) identifyServer(ctx context.Context) error {
	serverVersion, err := call[[]string](c.rpcClient, ctx,
		"server.version",
		clientName, clientVersion,
	)
	if err != nil {
		return err
	}

	c.serverImplementation = serverVersion[0]
	c.protocolVersion = serverVersion[1]

	return nil
}

// GetServerAddress returns the server address the client connects to.
func (c *client) GetServerAddress() string {
	return c.serverAddress
}

// GetServerImplementation returns the advertised server implementation returned in the first
// `server.version` method call.
func (c *client) GetServerImplementation() string {
	return c.serverImplementation
}

// GetProtocolVersion returns the advertised protocol implementation returned in the first
// `server.version` method call.
func (c *client) GetProtocolVersion() string {
	return c.protocolVersion
}

// Disconnect cuts the connection to the Electrum server, if connected.
func (c *client) Disconnect(ctx context.Context) {
	if c == nil || c.rpcClient == nil {
		return
	}

	slogctx.Info(ctx, "Disconnecting from Electrum server",
		"server_address", c.serverAddress,
		"server_implementation", c.serverImplementation,
		"protocol_version", c.protocolVersion,
	)

	if err := c.rpcClient.disconnect(); err != nil {
		slogctx.Error(ctx, "Found errors while disconnecting", "error", err)
	}

	c.rpcClient = nil
}

// IsConnected returns whether this client is still connected to the Electrum server.
func (c *client) IsConnected() bool {
	return c.rpcClient != nil
}

// ------------------ Electrum method calls ------------------ //

// ServerFeatures calls the `server.features` method and returns the relevant part of the result.
func (c *client) ServerFeatures(ctx context.Context) (ServerFeatures, error) {
	return call[ServerFeatures](c.rpcClient, ctx, "server.features")
}

// ServerPeers calls the `server.peers.subscribe` method and returns a list of server addresses.
func (c *client) ServerPeers(ctx context.Context) ([]string, error) {
	result, err := call[[]any](c.rpcClient, ctx, "server.peers.subscribe")
	if err != nil {
		return nil, err
	}

	var peers []string
	for _, entry := range result {
		if peer := c.parseServerPeersEntry(entry); peer != "" {
			peers = append(peers, peer)
		}
	}

	return peers, nil
}

// parseServerPeersEntry parses a single entry in the result payload of `server.peers.subscribe`.
// Returns empty if payload couldn't been parsed.
func (c *client) parseServerPeersEntry(serverPeersEntry any) string {
	// `server.peers.subscribe` method returns this monstrosity:
	//	[ "<ip>", "<domain>", ["<version>", "s<SSL port>", "t<TCP port>"] ]
	// Ports can be in any order, or absent if the protocol is not supported

	array, ok := serverPeersEntry.([]any)
	if !ok || len(array) < 3 {
		return ""
	}

	address, ok := array[1].(string)
	if !ok {
		return ""
	}

	portArray, ok := array[2].([]any)
	if !ok {
		return ""
	}

	var sslPort, tcpPort string
	for _, port := range portArray {
		strPort, ok := port.(string)
		if ok && len(strPort) > 0 {
			if strPort[0] == 's' {
				sslPort = strPort[1:]
			} else if strPort[0] == 't' {
				tcpPort = strPort[1:]
			}
		}
	}

	// prioritize TLS connections
	if sslPort != "" {
		return "tls://" + address + ":" + sslPort
	}

	// but accept TCP connections if there's nothing else
	if tcpPort != "" {
		return "tcp://" + address + ":" + tcpPort
	}

	return ""
}

// Broadcast calls the `blockchain.transaction.broadcast` endpoint and returns the transaction hash.
func (c *client) Broadcast(ctx context.Context, rawTx string) (string, error) {
	return call[string](c.rpcClient, ctx, "blockchain.transaction.broadcast", rawTx)
}

// BroadcastBatch is `Broadcast` with batching.
func (c *client) BroadcastBatch(
	ctx context.Context, rawTxs []string,
) (map[string]Result[string], error) {
	return callBatch1Param[string](c.rpcClient, ctx, "blockchain.transaction.broadcast", rawTxs)
}

// GetBestBlockHeight calls the `blockchain.headers.subscribe` endpoint and responds with the best
// block height.
func (c *client) GetBestBlockHeight(ctx context.Context) (int, error) {
	headers, err := c.GetHeaders(ctx)
	if err != nil {
		return 0, err
	}
	return int(headers.Height), nil
}

// GetHeaders calls the `blockchain.headers.subscribe` endpoint and responds
// with the current block height and hash.
func (c *client) GetHeaders(ctx context.Context) (GetHeadersResult, error) {
	return call[GetHeadersResult](c.rpcClient, ctx, "blockchain.headers.subscribe")
}

// GetScriptHashHistory calls `blockchain.scripthash.get_history` and returns the transaction
// history for the given script hash.
func (c *client) GetScriptHashHistory(
	ctx context.Context, scriptHash string,
) ([]ScriptHashHistoryEntry, error) {
	return call[[]ScriptHashHistoryEntry](c.rpcClient, ctx,
		"blockchain.scripthash.get_history",
		scriptHash,
	)
}

// GetScriptHashHistoryBatch is `GetScriptHashHistory` with batching.
func (c *client) GetScriptHashHistoryBatch(
	ctx context.Context, scriptHashes []string,
) (map[string]Result[[]ScriptHashHistoryEntry], error) {
	return callBatch1Param[[]ScriptHashHistoryEntry](c.rpcClient, ctx,
		"blockchain.scripthash.get_history",
		scriptHashes,
	)
}

// GetTransaction calls the `blockchain.transaction.get` endpoint and returns the transaction hex.
func (c *client) GetTransaction(ctx context.Context, txID string) (string, error) {
	return call[string](c.rpcClient, ctx, "blockchain.transaction.get", txID)
}

// GetTransactionBatch is `GetTransaction` with batching.
func (c *client) GetTransactionBatch(
	ctx context.Context, txIDs []string,
) (map[string]Result[string], error) {
	return callBatch1Param[string](c.rpcClient, ctx, "blockchain.transaction.get", txIDs)
}

// ListUnspent calls `blockchain.scripthash.listunspent` and returns the UTXO results.
func (c *client) ListUnspent(ctx context.Context, indexHash string) ([]UnspentRef, error) {
	return call[[]UnspentRef](c.rpcClient, ctx, "blockchain.scripthash.listunspent", indexHash)
}

// ListUnspentBatch is `ListUnspent` with batching.
func (c *client) ListUnspentBatch(
	ctx context.Context, indexHashes []string,
) (map[string]Result[[]UnspentRef], error) {
	return callBatch1Param[[]UnspentRef](c.rpcClient, ctx,
		"blockchain.scripthash.listunspent",
		indexHashes,
	)
}
