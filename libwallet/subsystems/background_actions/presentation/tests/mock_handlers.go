package tests

import (
	"context"
	"sync"
	"testing"

	"github.com/stretchr/testify/require"

	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// mockHandlers are the handlers of the test functions. Each returns nil unless the test sets
// its behavior, and every call is recorded in order.
type mockHandlers struct {
	t         func() *testing.T
	mutex     sync.Mutex
	handlers  map[ba_model.Function]ba_model.Handler
	behaviors map[ba_model.Function]ba_model.Handler
	ran       []ba_model.Function
}

func newMockHandlers(t func() *testing.T, functions ...ba_model.Function) *mockHandlers {
	h := &mockHandlers{
		t:         t,
		handlers:  map[ba_model.Function]ba_model.Handler{},
		behaviors: map[ba_model.Function]ba_model.Handler{},
	}

	for _, fn := range functions {
		h.handlers[fn] = func(ctx context.Context) error {
			h.t().Helper()
			return h.runHandler(ctx, fn)
		}
	}

	return h
}

func (h *mockHandlers) runHandler(ctx context.Context, fn ba_model.Function) error {
	h.t().Helper()

	h.mutex.Lock()
	h.ran = append(h.ran, fn)
	behavior := h.behaviors[fn]
	h.mutex.Unlock()

	if behavior == nil {
		return nil
	}

	return behavior(ctx)
}

// set makes fn run behavior from now on.
func (h *mockHandlers) set(fn ba_model.Function, behavior ba_model.Handler) {
	h.t().Helper()

	h.mutex.Lock()
	defer h.mutex.Unlock()

	h.behaviors[fn] = behavior
}

// reset forgets the behaviors set and the functions run.
func (h *mockHandlers) reset() {
	h.t().Helper()

	h.mutex.Lock()
	defer h.mutex.Unlock()

	h.behaviors = map[ba_model.Function]ba_model.Handler{}
	h.ran = nil
}

// requireRan checks that the functions run since the last check are functions, in order.
func (h *mockHandlers) requireRan(functions ...ba_model.Function) {
	h.t().Helper()

	h.mutex.Lock()
	defer h.mutex.Unlock()

	ran := h.ran
	h.ran = nil

	if len(functions) == 0 {
		require.Empty(h.t(), ran)
	} else {
		require.Equal(h.t(), functions, ran)
	}
}

func (h *mockHandlers) requireNoRan() {
	h.t().Helper()
	h.requireRan()
}
