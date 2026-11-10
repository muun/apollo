package otel

import (
	"go.opentelemetry.io/otel/attribute"
)

const (
	defaultServiceName = "libwallet"
	defaultEndpoint    = "api.honeycomb.io:443"
)

// Config is the input NewSetup uses to set up OTel.
type Config struct {
	// ServiceName will be automatically included as an attribute in every exported span.
	ServiceName string

	// HoneycombAPIKey is the Honeycomb team key. An empty value disables Honeycomb export.
	HoneycombAPIKey string

	// HoneycombDataset is the Honeycomb Classic dataset to use. Optional.
	HoneycombDataset string

	// HoneycombEndpoint is the Honeycomb endpoint used.
	HoneycombEndpoint string

	// LogSpans toggles logging spans when they end.
	LogSpans bool

	// ExtraAttrs are additional tags to inject into all newly created spans.
	ExtraAttrs []attribute.KeyValue
}

// NewConfigFromMap builds a Config from the environment values provided in a map:
//
//	ServiceName: "libwallet"
//	APIKey: env["HONEYCOMB_KEY"]
//	Dataset: env["ENV"]
//	Endpoint: env["HONEYCOMB_ENDPOINT"] || "api.honeycomb.io:443"
//	LogSpans: env["CI"] != "true" -> Turn off logs on CI.
//	ExtraAttrs["pod"]: env["HOSTNAME"]
//	ExtraAttrs["variant"]: env["VARIANT"]
//	ExtraAttrs["ci_run_id"]: env["CI_RUN_ID"]
//	ExtraAttrs["git_branch"]: env["GIT_BRANCH"]
func NewConfigFromMap(env map[string]string) *Config {
	endpoint := env["HONEYCOMB_ENDPOINT"]
	if endpoint == "" {
		endpoint = defaultEndpoint
	}

	return &Config{
		ServiceName:       defaultServiceName,
		HoneycombAPIKey:   env["HONEYCOMB_KEY"],
		HoneycombDataset:  env["ENV"],
		HoneycombEndpoint: endpoint,
		LogSpans:          env["CI"] != "true",
		ExtraAttrs: buildExtraAttrs(env, map[string]string{
			"pod":        "HOSTNAME",
			"variant":    "VARIANT",
			"ci_run_id":  "CI_RUN_ID",
			"git_branch": "GIT_BRANCH",
		}),
	}
}

// NewConfig builds a Config pointing at the default Honeycomb endpoint, with Honeycomb export and
// span logging off.
func NewConfig() *Config {
	return &Config{
		ServiceName:       defaultServiceName,
		HoneycombEndpoint: defaultEndpoint,
	}
}

// WithHoneycombAPIKey sets the Honeycomb team key. An empty key disables Honeycomb export.
func (c *Config) WithHoneycombAPIKey(key string) *Config {
	c.HoneycombAPIKey = key
	return c
}

// WithHoneycombDataset sets the Honeycomb Classic dataset. An empty value sends no dataset header.
func (c *Config) WithHoneycombDataset(dataset string) *Config {
	c.HoneycombDataset = dataset
	return c
}

// WithLogSpans toggles logging spans when they end.
func (c *Config) WithLogSpans(logSpans bool) *Config {
	c.LogSpans = logSpans
	return c
}

// WithExtraAttrs appends a tag for each non-empty value to the ones injected into all spans.
func (c *Config) WithExtraAttrs(extraAttrs map[string]string) *Config {
	for attrKey, attrValue := range extraAttrs {
		if attrValue != "" {
			c.ExtraAttrs = append(c.ExtraAttrs, attribute.String(attrKey, attrValue))
		}
	}

	return c
}

// buildExtraAttrs reads each (attribute name, env var name) pair and emits an attribute
// for each env var set.
func buildExtraAttrs(env map[string]string, extraAttrs map[string]string) []attribute.KeyValue {
	attrs := make([]attribute.KeyValue, 0, len(extraAttrs))

	for attrKey, envKey := range extraAttrs {
		if attrValue := env[envKey]; attrValue != "" {
			attrs = append(attrs, attribute.String(attrKey, attrValue))
		}
	}

	return attrs
}
