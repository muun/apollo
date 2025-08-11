package electrum

import (
	"bufio"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"sort"
	"strings"
	"time"
)

const defaultLoggerTag = "Electrum/?"
const connectionTimeout = time.Second * 30
const callTimeout = time.Second * 30
const messageDelim = byte('\n')
const noTimeout = 0

var implsWithBatching = []string{"ElectrumX"}

// Client is a TLS client that implements a subset of the Electrum protocol.
//
// It includes a minimal implementation of a JSON-RPC client, since the one provided by the
// standard library doesn't support features such as batching.
//
// It is absolutely not thread-safe. Every Client should have a single owner.
type Client struct {
	Server        string
	ServerImpl    string
	ProtoVersion  string
	nextRequestID int
	conn          net.Conn
	log           *slog.Logger
	requireTls    bool
}

// Request models the structure of all Electrum protocol requests.
type Request struct {
	ID     int     `json:"id"`
	Method string  `json:"method"`
	Params []Param `json:"params"`
}

// ErrorResponse models the structure of a generic error response.
type ErrorResponse struct {
	ID    int         `json:"id"`
	Error interface{} `json:"error"` // type varies among Electrum implementations.
}

// ServerVersionResponse models the structure of a `server.version` response.
type ServerVersionResponse struct {
	ID     int      `json:"id"`
	Result []string `json:"result"`
}

// ServerFeaturesResponse models the structure of a `server.features` response.
type ServerFeaturesResponse struct {
	ID     int            `json:"id"`
	Result ServerFeatures `json:"result"`
}

// ServerPeersResponse models the structure (or lack thereof) of a `server.peers.subscribe` response
type ServerPeersResponse struct {
	ID     int           `json:"id"`
	Result []interface{} `json:"result"`
}

// ListUnspentResponse models a `blockchain.scripthash.listunspent` response.
type ListUnspentResponse struct {
	ID     int          `json:"id"`
	Result []UnspentRef `json:"result"`
}

// GetHeadersResponse models the structure of a `blockchain.headers.subscribe` response.
type GetHeadersResponse struct {
	ID     int              `json:"id"`
	Result GetHeadersResult `json:"result"`
}

type GetHeadersResult struct {
	Height int32  `json:"height"`
	Hex    string `json:"hex"`
}

// GetTransactionResponse models the structure of a `blockchain.transaction.get` response.
type GetTransactionResponse struct {
	ID     int    `json:"id"`
	Result string `json:"result"`
}

// BroadcastResponse models the structure of a `blockchain.transaction.broadcast` response.
type BroadcastResponse struct {
	ID     int    `json:"id"`
	Result string `json:"result"`
}

// UnspentRef models an item in the `ListUnspentResponse` results.
type UnspentRef struct {
	TxHash string `json:"tx_hash"`
	TxPos  int    `json:"tx_pos"`
	Value  int64  `json:"value"`
	Height int    `json:"height"`
}

// ServerFeatures contains the relevant information from `ServerFeatures` results.
type ServerFeatures struct {
	ID            int    `json:"id"`
	GenesisHash   string `json:"genesis_hash"`
	HashFunction  string `json:"hash_function"`
	ServerVersion string `json:"server_version"`
	ProcotolMin   string `json:"protocol_min"`
	ProtocolMax   string `json:"protocol_max"`
	Pruning       int    `json:"pruning"`
}

// Param is a convenience type that models an item in the `Params` array of an Request.
type Param = interface{}

// NewClient creates an initialized Client instance.
func NewClient(requireTls bool, logger *slog.Logger) *Client {
	return &Client{
		log:        logger,
		requireTls: requireTls,
	}
}

// Connect establishes a TLS connection to an Electrum server.
func (c *Client) Connect(server string) error {
	c.Disconnect()

	c.log = c.log.With(slog.String("source", "Electrum/"+server))
	c.Server = server

	c.log.Info("Connecting")

	err := c.establishConnection()
	if err != nil {
		c.Disconnect()
		c.log.Error("Connect failed", "error", err)
		return err
	}

	// Before calling it a day send a test request (trust me), and as we do identify the server:
	err = c.identifyServer()
	if err != nil {
		c.Disconnect()
		c.log.Error("Identifying server failed", "error", err)
		return err
	}

	c.log.Info(fmt.Sprintf("Identified as %s (%s)", c.ServerImpl, c.ProtoVersion))

	return nil
}

// Disconnect cuts the connection (if connected) to the Electrum server.
func (c *Client) Disconnect() error {
	if c.conn == nil {
		return nil
	}

	c.log.Info("Disconnecting")

	err := c.conn.Close()
	if err != nil {
		c.log.Error("Disconnect failed", "error", err)
		return err
	}

	c.conn = nil
	return nil
}

// SupportsBatching returns whether this client can process batch requests.
func (c *Client) SupportsBatching() bool {
	for _, implName := range implsWithBatching {
		if strings.HasPrefix(c.ServerImpl, implName) {
			return true
		}
	}

	return false
}

// ServerVersion calls the `server.version` method and returns the [impl, protocol version] tuple.
func (c *Client) ServerVersion() ([]string, error) {
	request := Request{
		Method: "server.version",
		Params: []Param{},
	}

	var response ServerVersionResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("ServerVersion failed", "error", err)
		return nil, err
	}

	return response.Result, nil
}

// ServerFeatures calls the `server.features` method and returns the relevant part of the result.
func (c *Client) ServerFeatures() (*ServerFeatures, error) {
	request := Request{
		Method: "server.features",
		Params: []Param{},
	}

	var response ServerFeaturesResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("ServerFeatures failed", "error", err)
		return nil, err
	}

	return &response.Result, nil
}

// ServerPeers calls the `server.peers.subscribe` method and returns a list of server addresses.
func (c *Client) ServerPeers() ([]string, error) {
	res, err := c.rawServerPeers()
	if err != nil {
		return nil, err // note that, besides I/O errors, some servers close the socket on this request
	}

	var peers []string

	for _, entry := range res {
		// Get ready for some hot casting action. Not for the faint of heart.
		addr := entry.([]interface{})[1].(string)
		port := entry.([]interface{})[2].([]interface{})[1].(string)[1:]

		peers = append(peers, addr+":"+port)
	}

	return peers, nil
}

// rawServerPeers calls the `server.peers.subscribe` method and returns this monstrosity:
//
//	[ "<ip>", "<domain>", ["<version>", "s<SSL port>", "t<TLS port>"] ]
//
// Ports can be in any order, or absent if the protocol is not supported
func (c *Client) rawServerPeers() ([]interface{}, error) {
	request := Request{
		Method: "server.peers.subscribe",
		Params: []Param{},
	}

	var response ServerPeersResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("rawServerPeers failed", "error", err)
		return nil, err
	}

	return response.Result, nil
}

// Broadcast calls the `blockchain.transaction.broadcast` endpoint and returns the transaction hash.
func (c *Client) Broadcast(rawTx string) (string, error) {
	request := Request{
		Method: "blockchain.transaction.broadcast",
		Params: []Param{rawTx},
	}

	var response BroadcastResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("Broadcast failed", "error", err)
		return "", err
	}

	return response.Result, nil
}

// GetHeaders calls the `blockchain.headers.subscribe` endpoint and responds with the current block height and hash.
func (c *Client) GetHeaders() (*GetHeadersResult, error) {
	request := Request{
		Method: "blockchain.headers.subscribe",
		Params: []Param{},
	}

	var response GetHeadersResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("GetNumBlocks failed", "error", err)
		return nil, err
	}

	return &response.Result, nil
}

// GetTransaction calls the `blockchain.transaction.get` endpoint and returns the transaction hex.
func (c *Client) GetTransaction(txID string) (string, error) {
	request := Request{
		Method: "blockchain.transaction.get",
		Params: []Param{txID},
	}

	var response GetTransactionResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("GetTransaction failed", "error", err)
		return "", err
	}

	return response.Result, nil
}

// ListUnspent calls `blockchain.scripthash.listunspent` and returns the UTXO results.
func (c *Client) ListUnspent(indexHash string) ([]UnspentRef, error) {
	request := Request{
		Method: "blockchain.scripthash.listunspent",
		Params: []Param{indexHash},
	}
	var response ListUnspentResponse

	err := c.call(&request, &response, callTimeout)
	if err != nil {
		c.log.Error("ListUnspent failed", "error", err)
		return nil, err
	}

	return response.Result, nil
}

// ListUnspentBatch is like `ListUnspent`, but using batching.
func (c *Client) ListUnspentBatch(indexHashes []string) ([][]UnspentRef, error) {
	requests := make([]*Request, len(indexHashes))
	method := "blockchain.scripthash.listunspent"

	for i, indexHash := range indexHashes {
		requests[i] = &Request{
			Method: method,
			Params: []Param{indexHash},
		}
	}

	var responses []ListUnspentResponse

	// Give it a little more time than non-batch calls
	timeout := callTimeout * 2

	err := c.callBatch(method, requests, &responses, timeout)
	if err != nil {
		return nil, fmt.Errorf("ListUnspentBatch failed: %w", err)
	}

	// Don't forget to sort responses:
	sort.Slice(responses, func(i, j int) bool {
		return responses[i].ID < responses[j].ID
	})

	// Now we can collect all results:
	var unspentRefs [][]UnspentRef

	for _, response := range responses {
		unspentRefs = append(unspentRefs, response.Result)
	}

	return unspentRefs, nil
}

func (c *Client) establishConnection() error {
	// We first try to connect over TCP+TLS
	// If we fail and requireTls is false, we try over TCP

	// TODO: check if insecure is necessary
	config := &tls.Config{
		InsecureSkipVerify: true,
	}

	dialer := &net.Dialer{
		Timeout: connectionTimeout,
	}

	tlsConn, err := tls.DialWithDialer(dialer, "tcp", c.Server, config)
	if err == nil {
		c.conn = tlsConn
		return nil
	}
	if c.requireTls {
		return err
	}

	conn, err := net.DialTimeout("tcp", c.Server, connectionTimeout)
	if err != nil {
		return err
	}

	c.conn = conn

	return nil
}

func (c *Client) identifyServer() error {
	serverVersion, err := c.ServerVersion()
	if err != nil {
		return err
	}

	c.ServerImpl = serverVersion[0]
	c.ProtoVersion = serverVersion[1]

	c.log.Info(fmt.Sprintf("Identified %s %s", c.ServerImpl, c.ProtoVersion))

	return nil
}

// IsConnected returns whether this client is connected to a server.
// It does not guarantee the next request will succeed.
func (c *Client) IsConnected() bool {
	return c.conn != nil
}

// call executes a request with JSON marshalling, and loads the response into a pointer.
func (c *Client) call(request *Request, response interface{}, timeout time.Duration) error {
	// Assign a fresh request ID:
	request.ID = c.incRequestID()

	// Serialize the request:
	requestBytes, err := json.Marshal(request)
	if err != nil {
		c.log.Error("Marshal failed", "request", request, "error", err)
		return err
	}

	// Make the call, obtain the serialized response:
	responseBytes, err := c.callRaw(request.Method, requestBytes, timeout)
	if err != nil {
		c.log.Error("Send failed", "method", request.Method, "error", err)
		return err
	}

	// Deserialize into an error, to see if there's any:
	var maybeErrorResponse ErrorResponse

	err = json.Unmarshal(responseBytes, &maybeErrorResponse)
	if err != nil {
		c.log.Error("Unmarshal of potential error failed", "method", request.Method, "error", err)
		return err
	}

	if maybeErrorResponse.Error != nil {
		c.log.Error("Electrum error", "error", maybeErrorResponse.Error)
		return err
	}

	// Deserialize the response:
	err = json.Unmarshal(responseBytes, response)
	if err != nil {
		c.log.Error("Unmarshal failed", "response", string(responseBytes), "error", err)
		return err
	}

	return nil
}

// call executes a batch request with JSON marshalling, and loads the response into a pointer.
// Response may not match request order, so callers MUST sort them by ID.
func (c *Client) callBatch(
	method string, requests []*Request, response interface{}, timeout time.Duration,
) error {
	// Assign fresh request IDs:
	for _, request := range requests {
		request.ID = c.incRequestID()
	}

	// Serialize the request:
	requestBytes, err := json.Marshal(requests)
	if err != nil {
		c.log.Error("Marshal failed", "requests", requests, "error", err)
		return err
	}

	// Make the call, obtain the serialized response:
	responseBytes, err := c.callRaw(method, requestBytes, timeout)
	if err != nil {
		c.log.Error("Send failed", "method", method, "error", err)
		return err
	}

	// Deserialize into an array of errors, to see if there's any:
	var maybeErrorResponses []ErrorResponse

	err = json.Unmarshal(responseBytes, &maybeErrorResponses)
	if err != nil {
		c.log.Error("Unmarshal of potential error failed", "response", string(responseBytes), "error", err)
		return err
	}

	// Walk the responses, returning the first error found:
	for _, maybeErrorResponse := range maybeErrorResponses {
		if maybeErrorResponse.Error != nil {
			c.log.Error("Electrum error", "error", maybeErrorResponse.Error)
			return fmt.Errorf("%v", maybeErrorResponse.Error)
		}
	}

	// Deserialize the response:
	err = json.Unmarshal(responseBytes, response)
	if err != nil {
		c.log.Error("Unmarshal failed", "response", string(responseBytes), "error", err)
		return err
	}

	return nil
}

// callRaw sends a raw request in bytes, and returns a raw response (or an error).
func (c *Client) callRaw(method string, request []byte, timeout time.Duration) ([]byte, error) {
	c.log.Info(fmt.Sprintf("Sending %s request", method))
	c.log.Debug(fmt.Sprintf("Sending %s body: %s", method, string(request)))

	if !c.IsConnected() {
		err := fmt.Errorf("send failed %s: not connected", method)
		c.log.Error("Send failed: not connected", "method", method)
		return nil, err
	}

	request = append(request, messageDelim)

	start := time.Now()

	// SetDeadline is an absolute time based timeout. That is, we set an exact
	// time we want it to fail.
	var deadline time.Time
	if timeout == noTimeout {
		// This means no deadline
		deadline = time.Time{}
	} else {
		deadline = start.Add(timeout)
	}
	err := c.conn.SetDeadline(deadline)
	if err != nil {
		c.log.Error("Send failed: SetDeadline failed", "method", method, "error", err)
		return nil, err
	}

	_, err = c.conn.Write(request)

	if err != nil {
		duration := time.Since(start)
		c.log.Error("Send failed", "method", method, "duration", duration.Milliseconds(), "error", err)
		return nil, err
	}

	reader := bufio.NewReader(c.conn)

	response, err := reader.ReadBytes(messageDelim)
	duration := time.Since(start)
	if err != nil {
		c.log.Error("Receive failed", "method", method, "duration", duration.Milliseconds(), "error", err)
		return nil, err
	}

	c.log.Info(fmt.Sprintf("Received %s after %vms", method, duration.Milliseconds()))
	c.log.Debug(fmt.Sprintf("Received %s: %s", method, string(response)))

	return response, nil
}

func (c *Client) incRequestID() int {
	c.nextRequestID++
	return c.nextRequestID
}

// GetIndexHash returns the script parameter to use with Electrum, given a Bitcoin address.
func GetIndexHash(script []byte) string {
	indexHash := sha256.Sum256(script)
	reverse(&indexHash)

	return hex.EncodeToString(indexHash[:])
}

// reverse the order of the provided byte array, in place.
func reverse(a *[32]byte) {
	for i, j := 0, len(a)-1; i < j; i, j = i+1, j-1 {
		a[i], a[j] = a[j], a[i]
	}
}
