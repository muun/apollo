package tests

import (
	"sync"
	"sync/atomic"

	"github.com/muun/libwallet/app_provided_data"
)

type mockSessionProvider struct {
	loggedIn atomic.Bool

	mutex sync.Mutex
	err   error
}

func newMockSessionProvider() *mockSessionProvider {
	return &mockSessionProvider{}
}

func (p *mockSessionProvider) IsLoggedIn() (bool, error) {
	if err := p.failure(); err != nil {
		return false, err
	}

	return p.loggedIn.Load(), nil
}

func (p *mockSessionProvider) GetUser() (*app_provided_data.SessionUserResponse, error) {
	if err := p.failure(); err != nil {
		return nil, err
	}

	if !p.loggedIn.Load() {
		return &app_provided_data.SessionUserResponse{}, nil
	}

	return &app_provided_data.SessionUserResponse{
		User: &app_provided_data.SessionUser{HoustonID: 1},
	}, nil
}

// failWith makes every call fail with err.
func (p *mockSessionProvider) failWith(err error) {
	p.mutex.Lock()
	defer p.mutex.Unlock()

	p.err = err
}

// reset logs the user in and stops failing.
func (p *mockSessionProvider) reset() {
	p.loggedIn.Store(true)
	p.failWith(nil)
}

func (p *mockSessionProvider) failure() error {
	p.mutex.Lock()
	defer p.mutex.Unlock()

	return p.err
}
