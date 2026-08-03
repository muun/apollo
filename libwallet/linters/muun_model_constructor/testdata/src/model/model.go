package model

import (
	"encoding/json"
	"preconditions"
)

type Address struct {
	Street string
	City   string
}

func NewAddress(street, city string) *Address {
	return &Address{
		Street: street,
		City:   city,
	}
}

type User struct {
	Name    string
	Email   string
	Address Address
}

func NewUser(name, email string) *User {
	return &User{
		Name:  name,
		Email: email,
		Address: Address{ // want `use NewAddress instead of struct literal for model\.Address`
			Street: "default",
			City:   "default",
		},
	}
}

type Config struct {
	Key   string
	Value string
}

func helperFunc() *Address {
	return &Address{ // want `use NewAddress instead of struct literal for model\.Address`
		Street: "x",
		City:   "y",
	}
}

type internalItem struct {
	Value string
}

func newInternalItem(value string) *internalItem {
	return &internalItem{Value: value}
}

func badInternalItem() *internalItem {
	return &internalItem{ // want `use newInternalItem instead of struct literal for model\.internalItem`
		Value: "x",
	}
}

func badZeroValueVar() Address {
	var a Address // want `use NewAddress instead of zero-value var for model\.Address`
	return a
}

// ValidatedModel has a panicking constructor and a proper UnmarshalJSON.
type ValidatedModel struct { // want ValidatedModel:"constructor panics"
	Value string
}

func NewValidatedModel(value string) *ValidatedModel {
	if value == "" {
		panic("value required")
	}
	return &ValidatedModel{Value: value}
}

func (m *ValidatedModel) UnmarshalJSON(data []byte) error {
	var raw struct{ Value string }
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	*m = *NewValidatedModel(raw.Value)
	return nil
}

// UnprotectedModel has a panicking constructor but no UnmarshalJSON.
type UnprotectedModel struct { // want UnprotectedModel:"constructor panics"
	Score int
}

func NewUnprotectedModel(score int) *UnprotectedModel {
	if score < 0 {
		panic("score must be non-negative")
	}
	return &UnprotectedModel{Score: score}
}

// PreconditionModel validates via preconditions.CheckState, not direct panic.
type PreconditionModel struct { // want PreconditionModel:"constructor panics"
	Name string
}

func NewPreconditionModel(name string) *PreconditionModel {
	preconditions.CheckState(name != "")
	return &PreconditionModel{Name: name}
}
