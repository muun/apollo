package logging

import (
	"context"
	"log/slog"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/muun/libwallet/app_provided_data"
)

// bridgeHandler hands slog records to the app as lines with the level, the position of the log
// call, and the message followed by key=value for each top-level attribute, separated by ", ".
// The values are encoded by slog's JSONHandler.
type bridgeHandler struct {
	jsonHandler slog.Handler
	output      *bridgeOutput
	sink        app_provided_data.AppLogSink
}

var _ slog.Handler = (*bridgeHandler)(nil)

// NewBridgeHandler returns a handler that sends the records at the sink's min log level or above
// to sink.
func NewBridgeHandler(sink app_provided_data.AppLogSink) slog.Handler {
	level := slog.Level(sink.GetMinLogLevel())
	output := &bridgeOutput{}
	jsonHandler := slog.NewJSONHandler(output, &slog.HandlerOptions{Level: level})

	return &bridgeHandler{
		jsonHandler: jsonHandler,
		output:      output,
		sink:        sink,
	}
}

func (h *bridgeHandler) Enabled(ctx context.Context, level slog.Level) bool {
	return h.jsonHandler.Enabled(ctx, level)
}

func (h *bridgeHandler) Handle(ctx context.Context, record slog.Record) error {
	// The apps timestamp their lines, and JSONHandler leaves out a zero time.
	record.Time = time.Time{}

	file, line, function := "<libwallet>", 0, "<unknown>"
	if frame, _ := runtime.CallersFrames([]uintptr{record.PC}).Next(); frame.File != "" {
		file, line = filepath.Base(frame.File), frame.Line
		function = strings.TrimSuffix(frame.Function, "[...]") // a generic function
		function = function[strings.LastIndexByte(function, '.')+1:]
	}

	// JSONHandler encodes unlocked, so values that log while encoded don't deadlock, then makes
	// "a single serialized call to io.Writer.Write", which locks h.output and can't fail.
	// We don't unlock until the log has reached the native side.
	_ = h.jsonHandler.Handle(ctx, record)
	defer h.output.mutex.Unlock()

	return h.sink.Log(int(record.Level), h.output.message(record.Message), file, line, function)
}

func (h *bridgeHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return &bridgeHandler{
		jsonHandler: h.jsonHandler.WithAttrs(attrs),
		output:      h.output,
		sink:        h.sink,
	}
}

func (h *bridgeHandler) WithGroup(name string) slog.Handler {
	return &bridgeHandler{
		jsonHandler: h.jsonHandler.WithGroup(name),
		output:      h.output,
		sink:        h.sink,
	}
}

// bridgeOutput is the writer of a bridgeHandler's JSONHandler, shared by the handlers derived
// from it. Write locks it until the sink has the line, as the apps' loggers aren't all safe for
// concurrent use.
type bridgeOutput struct {
	mutex   sync.Mutex
	encoded []byte
}

func (o *bridgeOutput) Write(encoded []byte) (int, error) {
	o.mutex.Lock()
	o.encoded = append(o.encoded[:0], encoded...)
	return len(encoded), nil
}

// message returns the string to be logged.
// The format is `msg key=value, key2=value2` where all values are JSON serialized.
//
// To achieve this we take the output from JSONHandler and flatten the first level.
// We also skip the level and msg attribute, and prepend the original unquoted msg.
func (o *bridgeOutput) message(msg string) string {
	var message strings.Builder
	message.Grow(len(msg) + len(o.encoded))
	message.WriteString(msg)

	separator := " "
	if message.Len() == 0 {
		separator = ""
	}

	// strip the braces and the newline JSONHandler always includes
	members := o.encoded[1 : len(o.encoded)-2]
	start, colon, depth, inString, inBuiltinAttrs := 0, -1, 0, false, true

	for i := 0; i <= len(members); i++ {
		// Check whether a member ends here
		if i == len(members) || (members[i] == ',' && depth == 0 && !inString) {
			key := string(members[start+1 : colon-1]) // the key without its quotes

			// We write the previous member now that we finished reading it.
			// We skip all the builtin attributes added automatically by JSONHandler.
			// They are always at the start and end with the msg attribute.
			if !inBuiltinAttrs {
				message.WriteString(separator)
				message.WriteString(key)
				message.WriteByte('=')
				message.Write(members[colon+1 : i])
				separator = ", "
			}
			inBuiltinAttrs = inBuiltinAttrs && key != "msg"

			start, colon = i+1, -1
			continue
		}

		switch char := members[i]; {
		case inString && char == '\\':
			i++
		case char == '"':
			inString = !inString
		case inString:
		case char == ':' && colon < 0:
			colon = i
		case char == '{' || char == '[':
			depth++
		case char == '}' || char == ']':
			depth--
		}
	}

	// msg and json.Marshaler output can hold invalid UTF-8, and gomobile hands iOS nil for it.
	// Checking for the rare invalid case is cheaper than always calling ToValidUTF8.
	result := message.String()
	if !utf8.ValidString(result) {
		result = strings.ToValidUTF8(result, "\uFFFD")
	}
	return result
}
