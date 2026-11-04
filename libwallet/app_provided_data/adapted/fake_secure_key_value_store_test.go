package adapted

import (
	"bytes"

	"github.com/muun/libwallet/app_provided_data"
)

// fakeSecureKeyValueStore fakes the SecureKeyValueStore bridge over an in-memory map. It
// keeps the last Get response so tests can check it was wiped.
type fakeSecureKeyValueStore struct {
	values  map[string][]byte
	lastGet *app_provided_data.SecureKvGetResponse
}

func newFakeSecureKeyValueStore(values map[string][]byte) *fakeSecureKeyValueStore {
	return &fakeSecureKeyValueStore{values: values}
}

func (s *fakeSecureKeyValueStore) Get(key string) (*app_provided_data.SecureKvGetResponse, error) {
	value, ok := s.values[key]
	if !ok {
		s.lastGet = &app_provided_data.SecureKvGetResponse{
			StatusCode: app_provided_data.SecureKvStatusNotFound,
		}
		return s.lastGet, nil
	}
	s.lastGet = &app_provided_data.SecureKvGetResponse{
		StatusCode: app_provided_data.SecureKvStatusOk,
		Value:      bytes.Clone(value),
	}
	return s.lastGet, nil
}

func (s *fakeSecureKeyValueStore) Put(string, []byte) (*app_provided_data.SecureKvResponse, error) {
	panic("unimplemented")
}

func (s *fakeSecureKeyValueStore) Delete(string) (*app_provided_data.SecureKvResponse, error) {
	panic("unimplemented")
}

func (s *fakeSecureKeyValueStore) Wipe() (*app_provided_data.SecureKvResponse, error) {
	panic("unimplemented")
}
