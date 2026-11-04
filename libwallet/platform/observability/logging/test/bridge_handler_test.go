package test

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"net"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/observability/logging"
)

func TestBridgeHandlerMessage(t *testing.T) {
	tests := []struct {
		name string
		log  func(logger *slog.Logger)
		want string
	}{
		{
			name: "a span line",
			log: func(logger *slog.Logger) {
				logger.Info("libwallet Get", "trace_id", "4bf92f35", "span_duration_ms", int64(3),
					"status_code", "OK")
			},
			want: `libwallet Get trace_id="4bf92f35", span_duration_ms=3, status_code="OK"`,
		},
		{
			name: "no attributes",
			log:  func(logger *slog.Logger) { logger.Info("plain") },
			want: "plain",
		},
		{
			name: "no message",
			log:  func(logger *slog.Logger) { logger.Info("", "k", 1) },
			want: "k=1",
		},
		{
			name: "scalars",
			log: func(logger *slog.Logger) {
				logger.Info("m", "int", -42, "float", 1.5, "bool", true, "nil", nil,
					"duration", 1500*time.Millisecond,
					"time", time.Date(2026, 10, 8, 12, 0, 0, 0, time.UTC))
			},
			want: `m int=-42, float=1.5, bool=true, nil=null, duration=1500000000, ` +
				`time="2026-10-08T12:00:00Z"`,
		},
		{
			name: "errors and marshaled values",
			log: func(logger *slog.Logger) {
				logger.Info("m", "error", errors.New(`refused: "x"`), "ip", net.IPv4(1, 2, 3, 4),
					"bytes", []byte("hi"), "struct", point{X: 1, Y: 2, hidden: "h"},
					"map", map[string]int{"z": 1, "a": 2}, "slice", []string{"x", "y"})
			},
			want: `m error="refused: \"x\"", ip="1.2.3.4", bytes="aGk=", struct={"X":1,"Y":2}, ` +
				`map={"a":2,"z":1}, slice=["x","y"]`,
		},
		{
			name: "values that fail to encode",
			log: func(logger *slog.Logger) {
				logger.Info("m", "nan", math.NaN(), "panic", panickingMarshaler{})
			},
			want: `m nan="!ERROR:json: unsupported value: NaN", panic="!PANIC: boom"`,
		},
		{
			name: "escapes",
			log: func(logger *slog.Logger) {
				logger.Info("m", "s", "a\nb\tc \"q\" \\ \x01 \u2028 <&>")
			},
			want: `m s="a\nb\tc \"q\" \\ \u0001 \u2028 <&>"`,
		},
		{
			name: "separators inside values",
			log: func(logger *slog.Logger) {
				logger.Info("m", "note", `a, b: {c} [d]`, "map", map[string]string{"k,:{": "v]"})
			},
			want: `m note="a, b: {c} [d]", map={"k,:{":"v]"}`,
		},
		{
			name: "With and WithGroup chains",
			log: func(logger *slog.Logger) {
				logger.With("a", 1).WithGroup("g").With("b", 2).WithGroup("h").
					Info("m", "c", 3, slog.Group("i", "d", 4))
			},
			want: `m a=1, g={"b":2,"h":{"c":3,"i":{"d":4}}}`,
		},
		{
			name: "group attributes",
			log: func(logger *slog.Logger) {
				logger.Info("m", slog.Group("g", "a", 1, slog.Group("h", "b", "x")),
					slog.Group("", "inlined", true))
			},
			want: `m g={"a":1,"h":{"b":"x"}}, inlined=true`,
		},
		{
			name: "empty groups",
			log:  func(logger *slog.Logger) { logger.WithGroup("g").Info("m", slog.Group("e")) },
			want: "m",
		},
		{
			name: "repeated keys",
			log:  func(logger *slog.Logger) { logger.With("k", 1).Info("m", "k", 2, "k", 3) },
			want: "m k=1, k=2, k=3",
		},
		{
			name: "attributes named like the built-ins",
			log: func(logger *slog.Logger) {
				logger.With("level", 1).
					Info("m", "time", "t", "level", "l", "msg", "x", "source", "s")
			},
			want: `m level=1, time="t", level="l", msg="x", source="s"`,
		},
		{
			name: "invalid UTF-8",
			log: func(logger *slog.Logger) {
				logger.Info("a\xffb", "k\xff", "v\xff", "raw", json.RawMessage("\"x\xffy\""))
			},
			want: `a` + "\uFFFD" + `b k\ufffd="v\ufffd", raw="x` + "\uFFFD" + `y"`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			sink, handler := newBridgeHandler()
			tt.log(slog.New(handler))

			require.Equal(t, []string{tt.want}, sink.messages())
		})
	}
}

func TestBridgeHandlerSendsLevelAndCallSite(t *testing.T) {
	sink := newMockAppLogSink(slog.LevelWarn)
	logger := slog.New(logging.NewBridgeHandler(sink))

	logger.Info("below the level")
	_, _, line, _ := runtime.Caller(0)
	logger.Warn("warned")
	unknown := slog.NewRecord(time.Now(), slog.LevelError, "no source", 0)
	require.NoError(t, logger.Handler().Handle(t.Context(), unknown))

	require.Equal(t, []sinkLine{
		{
			level:    int(slog.LevelWarn),
			message:  "warned",
			file:     "bridge_handler_test.go",
			line:     line + 1,
			function: "TestBridgeHandlerSendsLevelAndCallSite",
		},
		{
			level:    int(slog.LevelError),
			message:  "no source",
			file:     "<libwallet>",
			function: "<unknown>",
		},
	}, sink.lines)
}

func TestBridgeHandlerNamesGenericFunctions(t *testing.T) {
	sink, handler := newBridgeHandler()

	logFromGeneric[int](slog.New(handler))

	require.Len(t, sink.lines, 1)
	require.Equal(t, "logFromGeneric", sink.lines[0].function)
}

func TestBridgeHandlerLogsFromValuesBeingEncoded(t *testing.T) {
	sink, handler := newBridgeHandler()
	logger := slog.New(handler)

	done := make(chan struct{})
	go func() {
		defer close(done)
		logger.Info("outer", "valuer", loggingValuer{logger: logger.With("w", 1)},
			"error", loggingError{logger: logger}, "marshaler", loggingMarshaler{logger: logger})
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		require.FailNow(t, "a value that logs while it's encoded blocked")
	}

	require.Equal(t, []string{
		"from LogValue w=1",
		"from Error",
		"from MarshalJSON",
		`outer valuer="v", error="e", marshaler="m"`,
	}, sink.messages())
}

func TestBridgeHandlerReturnsTheSinkError(t *testing.T) {
	sink, handler := newBridgeHandler()
	sink.err = errors.New("thrown by the app")

	// The second line only gets through if the first one released the lock.
	var errs []error
	done := make(chan struct{})
	go func() {
		defer close(done)
		for range 2 {
			record := slog.NewRecord(time.Now(), slog.LevelInfo, "m", 0)
			errs = append(errs, handler.Handle(t.Context(), record))
		}
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		require.FailNow(t, "a line the sink failed left the handler locked")
	}

	require.Equal(t, []error{sink.err, sink.err}, errs)
	require.Equal(t, []string{"m", "m"}, sink.messages())
}

func TestBridgeHandlerHandsLinesToTheSinkOneAtATime(t *testing.T) {
	sink, handler := newBridgeHandler()
	logger := slog.New(handler)
	loggers := []struct {
		logger *slog.Logger
		format string
	}{
		{logger: logger, format: "m g=%d, i=%d"},
		{logger: logger.With("w", 1), format: "m w=1, g=%d, i=%d"},
		{logger: logger.WithGroup("g"), format: `m g={"g":%d,"i":%d}`},
	}

	const goroutines, lines = 8, 150
	var want []string
	var wg sync.WaitGroup
	for g := range goroutines {
		for i := range lines {
			want = append(want, fmt.Sprintf(loggers[(g+i)%len(loggers)].format, g, i))
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := range lines {
				loggers[(g+i)%len(loggers)].logger.Info("m", "g", g, "i", i)
			}
		}()
	}
	wg.Wait()

	require.False(t, sink.overlapped.Load(), "two lines reached the sink at once")
	require.ElementsMatch(t, want, sink.messages())
}

// FuzzBridgeHandlerMessage checks the split against each attribute encoded by JSONHandler on its
// own.
func FuzzBridgeHandlerMessage(f *testing.F) {
	f.Add("m", "k", "v", "n")
	f.Add("", `a"b`, `a, b: {c} [d] "e" \`, `{"x":1}`)
	f.Add("\xff", "k\xff\\", "\u2028\x01\n", "")
	f.Fuzz(func(t *testing.T, msg, key, value, nested string) {
		sink, handler := newBridgeHandler()
		object := map[string]string{key: nested}
		slog.New(handler).Info(msg, key, value, "object", object)

		want := strings.ToValidUTF8(msg, "\uFFFD")
		if want != "" {
			want += " "
		}
		encodedKey := strings.TrimSuffix(strings.TrimPrefix(encodeAlone(t, key, "x"), `"`), `":"x"`)
		want += encodedKey + "=" + strings.TrimPrefix(encodeAlone(t, "k", value), `"k":`) +
			", object=" + strings.TrimPrefix(encodeAlone(t, "k", object), `"k":`)

		require.Equal(t, []string{want}, sink.messages())
	})
}

func newBridgeHandler() (*mockAppLogSink, slog.Handler) {
	sink := newMockAppLogSink(slog.LevelInfo)
	handler := logging.NewBridgeHandler(sink)
	return sink, handler
}

// encodeAlone returns the member that JSONHandler writes for an attribute alone.
func encodeAlone(t *testing.T, key string, value any) string {
	t.Helper()
	var out bytes.Buffer
	record := slog.NewRecord(time.Time{}, slog.LevelInfo, "", 0)
	record.Add(key, value)
	require.NoError(t, slog.NewJSONHandler(&out, nil).Handle(t.Context(), record))
	return strings.TrimSuffix(strings.TrimPrefix(out.String(), `{"level":"INFO","msg":"",`), "}\n")
}

type point struct {
	X, Y   int
	hidden string
}

type panickingMarshaler struct{}

func (panickingMarshaler) MarshalJSON() ([]byte, error) {
	panic("boom")
}

type loggingValuer struct{ logger *slog.Logger }

func (v loggingValuer) LogValue() slog.Value {
	v.logger.Info("from LogValue")
	return slog.StringValue("v")
}

type loggingError struct{ logger *slog.Logger }

func (e loggingError) Error() string {
	e.logger.Info("from Error")
	return "e"
}

type loggingMarshaler struct{ logger *slog.Logger }

func (m loggingMarshaler) MarshalJSON() ([]byte, error) {
	m.logger.Info("from MarshalJSON")
	return []byte(`"m"`), nil
}

func logFromGeneric[T any](logger *slog.Logger) {
	logger.Info("m")
}
