package diagnostic_mode

import (
	diagnostics_model "github.com/muun/libwallet/subsystems/diagnostics/presentation/model"
)

type SessionStore interface {
	Add(data *diagnostics_model.DiagnosticSessionData) error
	Get(id string) (*diagnostics_model.DiagnosticSessionData, bool)
	Delete(id string)
}

type sessionStore struct{}

func NewSessionStore() SessionStore {
	return &sessionStore{}
}

func (s *sessionStore) Add(
	data *diagnostics_model.DiagnosticSessionData,
) error {
	return AddDiagnosticSession(data)
}

func (s *sessionStore) Get(
	id string,
) (*diagnostics_model.DiagnosticSessionData, bool) {
	return GetDiagnosticSession(id)
}

func (s *sessionStore) Delete(id string) {
	DeleteDiagnosticSession(id)
}
