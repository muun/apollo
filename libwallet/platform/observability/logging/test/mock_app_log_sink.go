package test

import (
	"log/slog"
	"sync"
	"sync/atomic"
)

// mockAppLogSink keeps its lines, and flags a Log call that starts while another is in progress.
type mockAppLogSink struct {
	minLevel   slog.Level
	lines      []sinkLine
	err        error
	busy       sync.Mutex
	overlapped atomic.Bool
}

func newMockAppLogSink(minLevel slog.Level) *mockAppLogSink {
	return &mockAppLogSink{
		minLevel: minLevel,
	}
}

func (s *mockAppLogSink) GetMinLogLevel() int {
	return int(s.minLevel)
}

func (s *mockAppLogSink) Log(
	level int,
	message string,
	file string,
	line int,
	function string,
) error {
	if s.busy.TryLock() {
		defer s.busy.Unlock()
	} else {
		s.overlapped.Store(true)
	}

	s.lines = append(s.lines, sinkLine{
		level:    level,
		message:  message,
		file:     file,
		line:     line,
		function: function,
	})

	return s.err
}

func (s *mockAppLogSink) messages() []string {
	var messages []string
	for _, line := range s.lines {
		messages = append(messages, line.message)
	}
	return messages
}

type sinkLine struct {
	level    int
	message  string
	file     string
	line     int
	function string
}
