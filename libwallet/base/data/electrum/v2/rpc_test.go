package v2

import (
	"bufio"
	"encoding/json"
	"net"
	"testing"
)

// newTestClient creates a rpcClient wired to the client side of a net.Pipe.
// Returns the rpcClient and the server-side conn.
func newTestClient(t *testing.T) (*rpcClient, net.Conn) {
	t.Helper()
	serverConn, clientConn := net.Pipe()

	client := &rpcClient{
		conn:   clientConn,
		reader: bufio.NewReader(clientConn),
	}

	t.Cleanup(func() {
		_ = serverConn.Close()
		_ = client.disconnect()
	})

	return client, serverConn
}

// TestIsNotification verifies the classification of JSON-RPC messages into
// responses vs notifications, including edge cases.
func TestIsNotification(t *testing.T) {
	tests := []struct {
		name string
		msg  string
		want bool
	}{
		// Responses (have an id) -> false
		{
			name: "response with numeric id",
			msg:  `{"id":1,"result":{"height":100}}`,
			want: false,
		},
		{
			name: "response with id 0",
			msg:  `{"id":0,"result":"ok"}`,
			want: false,
		},
		{
			name: "response with string id",
			msg:  `{"id":"abc","result":"ok"}`,
			want: false,
		},
		// Error responses -> false (even with null/missing id)
		{
			name: "error response with id",
			msg:  `{"id":1,"error":"bad request"}`,
			want: false,
		},
		{
			name: "error response with null id",
			msg:  `{"id":null,"error":"parse error"}`,
			want: false,
		},
		{
			name: "error response without id field",
			msg:  `{"error":"parse error"}`,
			want: false,
		},
		// Notifications (no id, no error) → true
		{
			name: "notification with null id",
			msg:  `{"id":null,"method":"blockchain.headers.subscribe","params":[{"height":100}]}`,
			want: true,
		},
		{
			name: "notification without id field",
			msg:  `{"method":"blockchain.headers.subscribe","params":[{"height":100}]}`,
			want: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var msg message
			if err := json.Unmarshal([]byte(tt.msg), &msg); err != nil {
				t.Fatalf("unmarshal failed: %v", err)
			}
			got := msg.isNotification()
			if got != tt.want {
				t.Errorf("isNotification() = %v, want %v", got, tt.want)
			}
		})
	}
}

// TestUnparseableID verifies that non-numeric IDs fall back to the sentinel value 0.
func TestUnparseableID(t *testing.T) {
	rawMessage := `{"id":"not-a-number","result":"ok"}`
	var msg message
	if err := json.Unmarshal([]byte(rawMessage), &msg); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}

	resp := msg.toResponse()
	if resp.id != 0 {
		t.Errorf("id = %d, want 0 (sentinel for unparseable)", resp.id)
	}
}

// TestCallRawSkipsStaleResponses verifies that responses from previous timed-out calls (lower IDs)
// are discarded.
func TestCallRawSkipsStaleResponses(t *testing.T) {
	client, serverConn := newTestClient(t)
	client.nextRequestID = 4

	go func() {
		defer func() { _ = serverConn.Close() }()

		buf := make([]byte, 4096)
		if _, err := serverConn.Read(buf); err != nil {
			t.Errorf("failed to read response: %v", err)
			return
		}

		// Send a stale response (id=3) followed by the current one (id=5)
		messages := []string{
			`{"id":3,"result":"stale"}`,
			`{"id":5,"result":"current"}`,
		}
		for _, msg := range messages {
			if _, err := serverConn.Write([]byte(msg + "\n")); err != nil {
				t.Errorf("failed to write response: %v", err)
				return
			}
		}
	}()

	ctx := t.Context()
	result, err := call[string](client, ctx, "test.method")
	if err != nil {
		t.Fatalf("call failed: %v", err)
	}

	if result != "current" {
		t.Errorf("result = %q, want %q", result, "current")
	}
}

// TestCallRawSkipsStaleResponsesInBatch is the batch variant of stale response
// skipping, a stale single response followed by a valid batch response.
func TestCallRawSkipsStaleResponsesInBatch(t *testing.T) {
	client, serverConn := newTestClient(t)
	client.nextRequestID = 4

	go func() {
		defer func() { _ = serverConn.Close() }()

		buf := make([]byte, 4096)
		if _, err := serverConn.Read(buf); err != nil {
			t.Errorf("failed to read response: %v", err)
			return
		}

		// Stale response first, then a batch response with 2 valid results
		messages := []string{
			`{"id":1,"result":"stale"}`,
			`[{"id":5,"result":"first"},{"id":6,"result":"second"}]`,
		}
		for _, msg := range messages {
			if _, err := serverConn.Write([]byte(msg + "\n")); err != nil {
				t.Errorf("failed to write response: %v", err)
				return
			}
		}
	}()

	ctx := t.Context()
	reqs := []request{
		newRequest("test.method", "param1"),
		newRequest("test.method", "param2"),
	}
	results, err := callBatch[string](client, ctx, reqs)
	if err != nil {
		t.Fatalf("callBatch failed: %v", err)
	}

	if results[0].Value != "first" {
		t.Errorf("results[0] = %q, want `first`", results[0])
	}
	if results[1].Value != "second" {
		t.Errorf("results[1] = %q, want `second`", results[1])
	}
}
