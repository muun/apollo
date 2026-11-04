package provider_api

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// ResponseError is a provider's answer with an error HTTP status. The spec
// gives each status its own meaning per endpoint, so callers classify it by
// StatusCode.
type ResponseError struct {
	StatusCode int
	Message    string
}

// newResponseError builds the error for a response with the given status,
// taking the message from its {"error": "..."} body when it has one.
func newResponseError(statusCode int, body []byte) *ResponseError {
	message := http.StatusText(statusCode)

	var errorBody errorResponseJSON
	if json.Unmarshal(body, &errorBody) == nil && errorBody.Error != "" {
		message = errorBody.Error
	}

	return &ResponseError{
		StatusCode: statusCode,
		Message:    message,
	}
}

func (e *ResponseError) Error() string {
	return fmt.Sprintf("provider answered %d: %s", e.StatusCode, e.Message)
}

// errorResponseJSON is the body a provider answers errors with.
type errorResponseJSON struct {
	Error string `json:"error"`
}
