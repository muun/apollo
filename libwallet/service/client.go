package service

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/muun/libwallet/app_provided_data"
)

type Method string

const (
	MethodGet    = "GET"
	MethodPost   = "POST"
	MethodPut    = "PUT"
	MethodDelete = "DELETE"
)

const DefaultRetries = 3
const DefaultTimeout = 30 * time.Second
const AuthTokenSchemePrefix = "Bearer"

type HoustonResponseError struct {
	DeveloperMessage string
	ErrorCode        int
	Message          string
	RequestId        int
	Status           int
}

// Satisfy "error" interface for HoustonResponseError
func (e *HoustonResponseError) Error() string {
	errorJson, err := json.Marshal(e)
	if err != nil {
		// This should never happen since the error response should be unmarshalled previously
		slog.Error("failed to parse HoustonResponseError", slog.Any("error", err))
		return "failed to parse HoustonResponseError: " + err.Error()
	}
	return string(errorJson)
}

type request[T any] struct {
	Method string
	Path   string
	Params map[string]string
	Body   any
	// Retries defaults to DefaultRetries if nil.
	Retries *int
	// TimeoutPerAttempt defaults to DefaultTimeout if nil.
	TimeoutPerAttempt *time.Duration
}

type client struct {
	configurator app_provided_data.HttpClientSessionProvider
}

func (r request[T]) do(c *client) (T, error) {
	// FIXME: this needs a bunch of tests
	var zero T
	var result T

	if c.configurator == nil {
		return zero, fmt.Errorf("client.Do: CurrentRequestConfigurator not set")
	}

	session, err := c.configurator.Session()
	if err != nil {
		return zero, fmt.Errorf("client.Do: failed getting global headers: %w", err)
	}

	var body []byte
	if r.Body != nil {
		// FIXME: make sure we serialize things same as houston, mainly dates
		body, err = json.Marshal(r.Body)
		if err != nil {
			return zero, fmt.Errorf("client.Do: failed to marshall json: %w", err)
		}
	}

	idempotencyKey := uuid.New().String()

	baseUrl, err := url.Parse(session.BaseURL)
	if err != nil {
		return zero, fmt.Errorf("client.Do: failed to parse session BaseUrl: %w", err)
	}
	relativePath, err := url.Parse(r.Path)
	if err != nil {
		return zero, fmt.Errorf("client.Do: failed to parse request Path: %w", err)
	}
	httpUrl := baseUrl.ResolveReference(relativePath)

	httpRequest, err := http.NewRequest(
		r.Method,
		httpUrl.String(),
		bytes.NewReader(body),
	)
	if err != nil {
		return zero, fmt.Errorf("client.Do: failed to build request: %w", err)
	}

	header := httpRequest.Header
	header.Add("Content-Type", "application/json")
	header.Add("Accept", "application/json")
	header.Add("X-Client-Version", session.ClientVersion)
	header.Add("X-Client-Language", session.Language)
	header.Add("X-Client-Type", session.ClientType)
	header.Add("X-Idempotency-Key", idempotencyKey)
	header.Add("X-Client-Version-Name", session.ClientVersionName)
	if len(session.BackgroundExecutionMetrics) > 0 {
		header.Add("X-Background-Execution-Metrics", session.BackgroundExecutionMetrics)
	}
	if session.ClientType == "APOLLO" {
		header.Add("X-Client-Sdk-Version", session.ClientSdkVersion)
	}
	if session.ClientType == "FALCON" && len(session.DeviceToken) > 0 {
		header.Add("X-Device-Token", session.DeviceToken)
	}

	if len(session.AuthToken) > 0 {
		header.Add("Authorization", AuthTokenSchemePrefix+" "+session.AuthToken)
	}

	var lastError error

	var retries = DefaultRetries
	if r.Retries != nil {
		retries = *r.Retries
	}

	var timeout = DefaultTimeout
	if r.TimeoutPerAttempt != nil {
		timeout = *r.TimeoutPerAttempt
	}

	for attempt := 0; attempt < retries; attempt++ {
		header.Set("X-Retry-Count", strconv.Itoa(attempt))
		client := http.Client{
			Timeout: timeout,
		}
		response, err := client.Do(httpRequest)
		if err != nil {
			var urlError *url.Error
			lastError := fmt.Errorf("client.Do: request failed: %w", err)
			isUrlError := errors.As(err, &urlError)
			if isUrlError && (urlError.Timeout() || urlError.Temporary()) {
				// Retries are in order
				continue
			} else {
				return zero, lastError
			}
		}

		responseBody, err := io.ReadAll(response.Body)
		if err != nil {
			return zero, fmt.Errorf("client.Do: failed to parse error response: %w", err)
		}

		if response.StatusCode >= 400 {

			// Parse the error response
			houstonError := &HoustonResponseError{}
			err = json.Unmarshal(responseBody, houstonError)
			if err != nil {
				return zero, fmt.Errorf("client.Do: failed to parse error response: %w", err)
			}

			if response.StatusCode >= 500 {
				// Retries are in order
				lastError = houstonError
				continue
			}

			return zero, houstonError
		}

		authToken, found := bearerToken(response)
		if found {
			c.configurator.SetAuthToken(authToken)
		}
		c.configurator.SetMinClientVersion(response.Header.Get("X-Min-Client-Version"))
		c.configurator.SetSessionStatus(response.Header.Get("X-Session-Status"))
		if response.StatusCode == 204 {
			return zero, nil
		}

		return result, json.Unmarshal(responseBody, &result)
	}

	return zero, lastError
}

func bearerToken(response *http.Response) (token string, found bool) {
	parts := strings.Fields(response.Header.Get("Authorization"))
	if len(parts) == 2 && strings.EqualFold(parts[0], AuthTokenSchemePrefix) {
		return parts[1], true
	}
	return "", false
}
