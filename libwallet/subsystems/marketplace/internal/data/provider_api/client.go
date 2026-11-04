// Package provider_api calls the Provider API: the HTTP API every security
// cards provider serves to Muun's apps so they can buy cards directly from
// it. The contract lives in the Notion "Provider API" design doc.
package provider_api

import (
	"net/http"
)

// client sends requests to the providers' Provider APIs, reaching each
// provider at the base URL configurator resolves for it. Requests carry only
// what each endpoint needs: no Muun session data ever reaches a provider.
type client struct {
	configurator Configurator
	httpClient   *http.Client
}
