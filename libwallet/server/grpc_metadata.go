package server

import (
	"math"
	"runtime"
)

// metadataOverheadSize is the size reserved for non-trailer metadata that travels on every request.
const metadataOverheadSize = 1024

// maxTrailersTotalSize is the maximum amount of bytes allowed for the request trailers.
func maxTrailersTotalSize() int {
	// Get the client's raw limit.
	maxTrailerTotalSize := clientMetadataLimit()

	// Subtract the metadata overhead size.
	maxTrailerTotalSize -= metadataOverheadSize

	// Trailers travel as base64 encoded messages, so they take 4/3 more size.
	maxTrailerTotalSize = maxTrailerTotalSize / 4 * 3

	return maxTrailerTotalSize
}

// clientMetadataLimit is the maximum amount of bytes accepted by the gRPC client for request
// metadata. An oversized metadata make the whole gRPC request fail.
func clientMetadataLimit() int {
	// Ideally we would calculate this limit depending on what is advertised by each client
	// through the SETTINGS_MAX_HEADER_LIST_SIZE gRPC setting.
	// Unfortunately, grpc-go doesn't expose this value, and getting it ourselves is brittle.
	// Instead, we take advantage that by knowing the compilation target we can infer if the client
	// is Apollo or Falcon. We encode their respective limits here.
	// TODO: Make Falcon and Apollo explicitly send this constant.
	switch runtime.GOOS {
	case "android":
		// Apollo has no limit set (grpc-java, OkHttp transport).
		return math.MaxInt
	case "ios":
		// Falcon has a 16 KiB hardcoded limit (grpc-swift 1.x).
		return 16 * 1024
	default:
		// Host build: integration tests calling with grpc-go clients.
		// Assume the most restrictive real client.
		return 16 * 1024
	}
}
