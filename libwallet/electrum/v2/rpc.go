package v2

import (
	"bufio"
	"bytes"
	"cmp"
	"context"
	"crypto/tls"
	"encoding/json"
	"math"
	"net"
	"slices"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/observability/slogctx"
)

const (
	connectionTimeout = time.Second * 30
	singleCallTimeout = time.Second * 30
	batchCallTimeout  = time.Second * 60

	messageDelimiter = byte('\n')
)

type rpcClient struct {
	conn          net.Conn
	reader        *bufio.Reader
	nextRequestID int
}

func newRPCClient(
	ctx context.Context, address string, connType ConnectionType,
) (*rpcClient, error) {
	conn, err := establishConnection(ctx, address, connType)
	if err != nil {
		return nil, err
	}

	return &rpcClient{
		conn:          conn,
		reader:        bufio.NewReader(conn),
		nextRequestID: 0,
	}, nil
}

// establishConnection connects to the RPC server and returns the connection.
func establishConnection(
	ctx context.Context, address string, connType ConnectionType,
) (net.Conn, error) {
	var conn net.Conn
	var err error

	tcpDialer := &net.Dialer{
		Timeout: connectionTimeout,
	}

	if connType == RequireTLS || connType == PreferTLS {
		tlsDialer := tls.Dialer{
			NetDialer: tcpDialer,
			Config: &tls.Config{
				// TODO: check if insecure is necessary
				InsecureSkipVerify: true,
			},
		}

		conn, err = tlsDialer.DialContext(ctx, "tcp", address)
		if err == nil {
			return conn, nil
		}
	}

	if connType == RequireTCP || connType == PreferTLS {
		conn, err = tcpDialer.DialContext(ctx, "tcp", address)
		if err == nil {
			return conn, nil
		}
	}

	return nil, err
}

func (c *rpcClient) disconnect() error {
	if c == nil || c.conn == nil {
		return nil
	}

	err := c.conn.Close()
	c.conn = nil
	c.reader = nil
	return err
}

// call sends a single request, deserializes the result into T, and returns it.
func call[T any](c *rpcClient, ctx context.Context, method string, params ...any) (T, error) {
	results, err := callBatch[T](c, ctx, []request{newRequest(method, params...)})
	if err != nil {
		var value T
		return value, err
	}
	return results[0].Unwrap()
}

// callBatch1Param is like `callBatch` with better ergonomics for requests with a single param.
func callBatch1Param[T any, P comparable](
	c *rpcClient, ctx context.Context, method string, params []P,
) (map[P]Result[T], error) {
	requests := make([]request, len(params))
	for i, param := range params {
		requests[i] = newRequest(method, param)
	}

	results, err := callBatch[T](c, ctx, requests)
	if err != nil {
		return nil, err
	}

	mappedResults := make(map[P]Result[T], len(results))
	for i := range params {
		mappedResults[params[i]] = results[i]
	}

	return mappedResults, nil
}

// callBatch sends multiple requests as a JSON-RPC batch and deserializes each result into Result[T].
func callBatch[T any](c *rpcClient, ctx context.Context, requests []request) ([]Result[T], error) {
	// Make the call, obtain the serialized result
	rawResults, err := c.callRaw(ctx, requests)
	if err != nil {
		return nil, errors.Errorf("call raw: %w", err)
	}

	// Deserialize results
	results := make([]Result[T], len(rawResults))
	for i, rawResult := range rawResults {
		if rawResult.IsOk() {
			var value T
			if err = json.Unmarshal(rawResult.Value, &value); err != nil {
				return nil, errors.Errorf("result unmarshal failed for type %T: %w", value, err)
			}
			results[i] = okResult(value)
		} else {
			results[i] = errResult[T](rawResult.Err)
		}
	}

	return results, nil
}

// callRaw sends requests, collects responses, validates them, and returns the raw result payloads.
func (c *rpcClient) callRaw(
	ctx context.Context, requests []request,
) ([]Result[json.RawMessage], error) {
	if c == nil || c.conn == nil {
		return nil, errors.Errorf("client is disconnected")
	}
	if len(requests) == 0 {
		return nil, errors.Errorf("expected at least one request")
	}

	// Assign fresh req IDs
	for i := range requests {
		requests[i].ID = c.incRequestID()
	}

	slogctx.Info(ctx, "Sending requests", "requests", requests)

	// Set request timeout
	var timeout time.Duration
	if len(requests) == 1 {
		timeout = singleCallTimeout
	} else {
		timeout = batchCallTimeout
	}
	deadline := time.Now().Add(timeout)

	var cancel context.CancelFunc
	ctx, cancel = context.WithDeadline(ctx, deadline)
	defer cancel()

	if err := c.conn.SetDeadline(deadline); err != nil {
		return nil, errors.Errorf("SetDeadline failed: %w", err)
	}

	// Send requests
	if err := c.sendRequest(requests); err != nil {
		slogctx.Error(ctx, "Failed to send request",
			"requests", requests,
			"error", err,
		)
		return nil, errors.Errorf("send request: %w", err)
	}

	// Receive responses
	responses, err := c.receiveResponse(ctx, requests)
	if err != nil {
		slogctx.Error(ctx, "Failed to read responses",
			"requests", requests,
			"error", err,
		)
		return nil, errors.Errorf("receive response: %w", err)
	}

	slogctx.Info(ctx, "Received responses", "responses", responses)

	// Validate response length
	if len(responses) != len(requests) {
		return nil, errors.Errorf(
			"response count %d doesn't match request count %d",
			len(responses), len(requests),
		)
	}

	// Check all responses have valid IDs
	var noIDErrors []error
	for _, response := range responses {
		if response.id != 0 {
			continue
		}

		if response.isError() {
			noIDErrors = append(noIDErrors, errors.Errorf("response with unknown ID: %v", response))
		}
	}
	if len(noIDErrors) > 0 {
		return nil, errors.Join(noIDErrors...)
	}

	// Sort the responses by ID
	slices.SortFunc(responses, func(a response, b response) int {
		return cmp.Compare(a.id, b.id)
	})

	// Validate IDs match and build results array
	results := make([]Result[json.RawMessage], len(responses))
	for i, resp := range responses {
		// Validate ID
		if requests[i].ID != responses[i].id {
			return nil, errors.Errorf(
				"request and response ID doesn't match: request id %d, response id %d",
				requests[i].ID, responses[i].id,
			)
		}

		if resp.isError() {
			results[i] = errResult[json.RawMessage](c.parseElectrumError(resp.error))
		} else {
			results[i] = okResult(resp.result)
		}
	}

	return results, nil
}

// sendRequest serializes the request(s) as JSON and writes them to the connection.
// A single request is sent as a JSON object; multiple requests as a JSON array (batch).
func (c *rpcClient) sendRequest(requests []request) error {
	// Serialize requests
	var rawRequest []byte
	if len(requests) == 1 {
		rawReq, err := json.Marshal(requests[0])
		if err != nil {
			return errors.Errorf("single request marshalling failed: %w", err)
		}
		rawRequest = rawReq
	} else {
		rawReq, err := json.Marshal(requests)
		if err != nil {
			return errors.Errorf("batch requests marshalling failed: %w", err)
		}
		rawRequest = rawReq
	}

	rawRequest = append(rawRequest, messageDelimiter)

	writeStart := time.Now()

	_, err := c.conn.Write(rawRequest)
	if err != nil {
		duration := time.Since(writeStart)
		return errors.Errorf(
			"write request failed after %d millis: %w",
			duration.Milliseconds(), err,
		)
	}

	return nil
}

// receiveResponse reads from the connection until a non-stale response arrives.
// Stale responses (from previous timed-out calls) and notification messages are discarded.
func (c *rpcClient) receiveResponse(ctx context.Context, requests []request) ([]response, error) {
	// IDs are created on a strictly ascending order, so the first one is the minimum
	minReqID := requests[0].ID

	readStart := time.Now()

	for {
		rawMessage, err := c.reader.ReadBytes(messageDelimiter)
		if err != nil {
			duration := time.Since(readStart)
			return nil, errors.Errorf(
				"read response failed after %d millis: %w",
				duration.Milliseconds(), err,
			)
		}

		parsedMessages, err := c.parseIncomingMessage(rawMessage)
		if err != nil {
			return nil, err
		}

		responses := make([]response, 0, len(parsedMessages))
		for _, parsedMessage := range parsedMessages {
			if parsedMessage.isNotification() {
				c.processNotification(ctx, parsedMessage)
			} else {
				resp := parsedMessage.toResponse()
				if resp.id == 0 {
					slogctx.Warn(ctx, "Failed to parse response ID", "response", resp)
				}
				responses = append(responses, resp)
			}
		}
		if len(responses) == 0 {
			// Message was all notifications -> Wait for next message
			continue
		}

		// Skip stale responses from previous timed-out calls.
		// Responses with IDs lower than the min ID sent in request (excluding 0 for unknown)
		// are stale.
		minRespID := math.MaxInt
		for _, resp := range responses {
			if resp.id != 0 && resp.id < minRespID {
				minRespID = resp.id
			}
		}
		if minRespID < minReqID {
			slogctx.Warn(ctx, "Skipping stale response with lower ID than requested",
				"request", requests,
				"staleResponse", responses,
			)
			continue
		}

		return responses, nil
	}
}

// parseIncomingMessage parses a single JSON-RPC line into one or more messages.
// A line starting with '[' is treated as a batch message array; otherwise as a single message.
func (c *rpcClient) parseIncomingMessage(rawMessage []byte) ([]message, error) {
	rawMessage = bytes.TrimLeft(rawMessage, " \t\r\n")

	if rawMessage[0] == '[' {
		var batch []message
		if err := json.Unmarshal(rawMessage, &batch); err != nil {
			return nil, errors.Errorf("batch messages unmarshalling failed: %w", err)
		}
		return batch, nil
	}

	var single message
	if err := json.Unmarshal(rawMessage, &single); err != nil {
		return nil, errors.Errorf("single message unmarshalling failed: %w", err)
	}
	return []message{single}, nil
}

// parseElectrumError parses an Electrum protocol error returned by the server.
func (c *rpcClient) parseElectrumError(rawError json.RawMessage) error {
	var electrumError ElectrumError
	if err := json.Unmarshal(rawError, &electrumError); err != nil {
		return newUnstructuredElectrumError(string(rawError))
	}
	return electrumError
}

// processNotification parses and process a notification.
// Currently, we are just discarding the parsed notification.
// It is left as future work to figure out what we want to do with them.
// Errors are logged and ignored.
func (c *rpcClient) processNotification(ctx context.Context, msg message) {
	noti, err := msg.toNotification()
	if err != nil {
		slogctx.Error(ctx, "Failed to parse notification",
			"message", msg,
			"error", err,
		)
		// ignore error
		return
	}

	// TODO(lightning): Decide what to do with notifications
	slogctx.Info(ctx, "Discarding notification", "notification", noti)
}

// incRequestID generates monotonically increasing integer IDs for the requests
func (c *rpcClient) incRequestID() int {
	// ID 0 is reserved for responses with unknown ID
	c.nextRequestID++
	return c.nextRequestID
}
