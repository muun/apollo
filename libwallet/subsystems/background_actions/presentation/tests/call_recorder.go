package tests

import (
	"sync"
	"testing"

	"github.com/stretchr/testify/require"
)

// callRecorder records every call a mock bridge gets, and fails each one with its error in
// callErrs, or else with err.
type callRecorder struct {
	t        func() *testing.T
	mutex    sync.Mutex
	calls    []string
	err      error
	callErrs map[string]error
}

func newCallRecorder(t func() *testing.T) callRecorder {
	return callRecorder{t: t}
}

// requireCalls checks that the calls recorded since the last take are calls, in order.
func (r *callRecorder) requireCalls(calls ...string) {
	t := r.t()
	t.Helper()

	taken := r.takeCalls()
	if len(calls) == 0 {
		require.Empty(t, taken)
	} else {
		require.Equal(t, calls, taken)
	}
}

func (r *callRecorder) requireNoCalls() {
	r.t().Helper()
	r.requireCalls()
}

// takeCalls returns the calls recorded since the last take.
func (r *callRecorder) takeCalls() []string {
	r.t().Helper()

	r.mutex.Lock()
	defer r.mutex.Unlock()

	calls := r.calls
	r.calls = nil

	return calls
}

func (r *callRecorder) failWith(err error) {
	r.t().Helper()

	r.mutex.Lock()
	defer r.mutex.Unlock()

	r.err = err
}

// failCallWith makes call, as recorded, fail with err.
func (r *callRecorder) failCallWith(call string, err error) {
	r.t().Helper()

	r.mutex.Lock()
	defer r.mutex.Unlock()

	if r.callErrs == nil {
		r.callErrs = map[string]error{}
	}
	r.callErrs[call] = err
}

// reset forgets the recorded calls and stops failing.
func (r *callRecorder) reset() {
	r.t().Helper()

	r.mutex.Lock()
	defer r.mutex.Unlock()

	r.calls = nil
	r.err = nil
	r.callErrs = nil
}

func (r *callRecorder) record(call string) error {
	r.t().Helper()

	r.mutex.Lock()
	defer r.mutex.Unlock()

	r.calls = append(r.calls, call)

	if err, ok := r.callErrs[call]; ok {
		return err
	}

	return r.err
}
