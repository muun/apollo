package provider_api

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/url"

	"github.com/go-errors/errors"
)

// defaultRetries bounds how many times a request is sent when it times out or
// the provider fails with a server error.
const defaultRetries = 3

// maxResponseBytes caps how much of a provider's response is read.
const maxResponseBytes = 1 << 20

// request is a call to one endpoint of a provider's Provider API, whose JSON
// response decodes into a T.
type request[T any] struct {
	Method string
	Path   string
	Params map[string]string
}

// do sends the request to the given provider's Provider API through c and
// decodes its JSON response, sending it again when it times out or the
// provider fails with a server error, as long as ctx isn't done.
func (r request[T]) do(
	ctx context.Context,
	c *client,
	providerUUID string,
) (*T, error) {
	call := r.Method + " " + r.Path

	baseURL, err := c.configurator.BaseURL(providerUUID)
	if err != nil {
		return nil, errors.Errorf("%s: resolve base URL: %w", call, err)
	}
	parsedBaseURL, err := url.Parse(baseURL)
	if err != nil {
		return nil, errors.Errorf("%s: parse base URL %q: %w", call, baseURL, err)
	}
	httpURL := parsedBaseURL.JoinPath(r.Path)
	query := url.Values{}
	for key, value := range r.Params {
		query.Set(key, value)
	}
	httpURL.RawQuery = query.Encode()

	var lastError error
	for range defaultRetries {
		httpRequest, err := http.NewRequestWithContext(
			ctx, r.Method, httpURL.String(), nil,
		)
		if err != nil {
			return nil, errors.Errorf("%s: build request: %w", call, err)
		}
		httpRequest.Header.Set("Accept", "application/json")
		httpRequest.Header.Set("Content-Type", "application/json")

		response, err := c.httpClient.Do(httpRequest)
		if err != nil {
			lastError = errors.Errorf("%s: send request: %w", call, err)
			var netErr net.Error
			if errors.As(err, &netErr) && netErr.Timeout() && ctx.Err() == nil {
				continue
			}
			return nil, lastError
		}

		responseBody, err := io.ReadAll(io.LimitReader(response.Body, maxResponseBytes))
		_ = response.Body.Close()
		if err != nil {
			// The provider can stall after sending the headers, so the request
			// can also time out here.
			lastError = errors.Errorf("%s: read response: %w", call, err)
			var netErr net.Error
			if errors.As(err, &netErr) && netErr.Timeout() && ctx.Err() == nil {
				continue
			}
			return nil, lastError
		}

		if response.StatusCode >= http.StatusBadRequest {
			responseErr := newResponseError(response.StatusCode, responseBody)
			lastError = errors.Errorf("%s: %w", call, responseErr)
			if response.StatusCode >= http.StatusInternalServerError && ctx.Err() == nil {
				continue
			}
			return nil, lastError
		}

		var result T
		if err := json.Unmarshal(responseBody, &result); err != nil {
			return nil, errors.Errorf("%s: decode response: %w", call, err)
		}
		return &result, nil
	}

	return nil, lastError
}
