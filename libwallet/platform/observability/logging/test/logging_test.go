package test

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"runtime"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/observability/logging"
)

func TestHelpersReportTheirCallerAsTheSource(t *testing.T) {
	var out bytes.Buffer
	options := &slog.HandlerOptions{AddSource: true}
	ctx := logging.WithLogger(t.Context(), slog.New(slog.NewJSONHandler(&out, options)))

	helpers := []func(context.Context, string, ...any){
		logging.Debug, logging.Info, logging.Warn, logging.Error,
	}
	for _, helper := range helpers {
		helper(ctx, "m", "k", 1)
	}
	pc, file, line, _ := runtime.Caller(0)

	type logged struct {
		Level  string
		K      int
		Source slog.Source
	}
	var got []logged
	for decoder := json.NewDecoder(&out); decoder.More(); {
		var entry logged
		require.NoError(t, decoder.Decode(&entry))
		got = append(got, entry)
	}
	caller := slog.Source{Function: runtime.FuncForPC(pc).Name(), File: file, Line: line - 2}
	require.Equal(t, []logged{
		{Level: "INFO", K: 1, Source: caller},
		{Level: "WARN", K: 1, Source: caller},
		{Level: "ERROR", K: 1, Source: caller},
	}, got)
}
