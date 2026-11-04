package v2

import (
	"bytes"
	"encoding/json"
	"fmt"

	"github.com/go-errors/errors"
)

var nullMessage = []byte("null") // Cannot create a slice as constant, so it is var

// request models the structure of all Electrum protocol requests.
type request struct {
	ID     int    `json:"id"`
	Method string `json:"method"`
	Params []any  `json:"params"`
}

func newRequest(method string, params ...any) request {
	if params == nil {
		params = []any{}
	}
	return request{
		Method: method,
		Params: params,
	}
}

// response is a parsed JSON-RPC response. The id matches the request that produced it.
// An id of 0 means the ID could not be parsed (see toResponse).
type response struct {
	id     int
	result json.RawMessage // present only on success
	error  json.RawMessage // present only on failure
}

func (r response) isError() bool {
	// In JSON-RPC 1.0, Error can be "null" instead of missing
	return r.error != nil && !bytes.Equal(r.error, nullMessage)
}

func (r response) String() string {
	return fmt.Sprintf("{id: %d result: %s error: %s}", r.id, r.result, r.error)
}

// notification is a parsed JSON-RPC notification (server push with no request ID).
type notification struct {
	method string
	params []json.RawMessage
}

func (n notification) String() string {
	return fmt.Sprintf("{method:%s params:%s}", n.method, n.params)
}

// message is the raw JSON-RPC message received from the server before
// classification. Both responses and notifications arrive as the same JSON structure;
// isNotification() distinguishes them by checking whether an `id` field is present.
type message struct {
	// Missing on notifications
	ID json.RawMessage `json:"id"`

	// Present only on notifications
	Method json.RawMessage `json:"method"`
	Params json.RawMessage `json:"params"`

	// Present only on success responses
	Result json.RawMessage `json:"result"`

	// Present only on failure responses
	Error json.RawMessage `json:"error"`
}

func (m message) isNotification() bool {
	// In JSON-RPC 1.0, ID and Error can be "null" instead of missing
	isError := m.Error != nil && !bytes.Equal(m.Error, nullMessage)
	return !isError && (m.ID == nil || bytes.Equal(m.ID, nullMessage))
}

func (m message) isResponse() bool {
	return !m.isNotification()
}

// toNotification parses the message into a JSON-RPC notification
func (m message) toNotification() (*notification, error) {
	var noti notification

	if err := json.Unmarshal(m.Method, &noti.method); err != nil {
		return nil, errors.Errorf("notification method unmarshalling failed: %w", err)
	}

	if err := json.Unmarshal(m.Params, &noti.params); err != nil {
		return nil, errors.Errorf("notification params unmarshalling failed: %w", err)
	}

	return &noti, nil
}

// toResponse parses a responseOrNotification into a response. If the ID cannot be parsed
// (e.g. string IDs from non-standard servers), it is set to 0 as a sentinel for "unknown".
// Valid request IDs start at 1, so 0 is never ambiguous with a real response.
func (m message) toResponse() response {
	var id int
	if err := json.Unmarshal(m.ID, &id); err != nil {
		id = 0
	}

	return response{id: id, result: m.Result, error: m.Error}
}
