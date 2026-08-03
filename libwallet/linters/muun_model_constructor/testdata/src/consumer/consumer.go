package consumer

import (
	"bytes"
	"encoding/json"
	"model"
	"unrelated"
)

func badPointer() *model.User {
	return &model.User{ // want `use NewUser instead of struct literal for model\.User`
		Name:  "alice",
		Email: "alice@example.com",
	}
}

func badValue() model.User {
	return model.User{ // want `use NewUser instead of struct literal for model\.User`
		Name:  "bob",
		Email: "bob@example.com",
	}
}

func badNoConstructor() *model.Config {
	return &model.Config{ // want `use a constructor like NewConfig instead of struct literal for model\.Config`
		Key:   "k",
		Value: "v",
	}
}

func goodConstructor() *model.User {
	return model.NewUser("alice", "alice@example.com")
}

func goodUnrelated() *unrelated.Other {
	return &unrelated.Other{X: 1}
}

func badZeroValueVar() model.User {
	var u model.User // want `use NewUser instead of zero-value var for model\.User`
	return u
}

func goodZeroValueVarUnrelated() unrelated.Other {
	var o unrelated.Other
	return o
}

func goodVarWithInit() model.User {
	var u model.User = *model.NewUser("alice", "alice@example.com")
	return u
}

func goodPointerVar() *model.User {
	var u *model.User
	return u
}

// json.Unmarshal on type whose constructor doesn't panic — allowed.
func goodJSONUnmarshalNoPanic(data []byte) (*model.User, error) {
	var u model.User // want `use NewUser instead of zero-value var for model\.User`
	err := json.Unmarshal(data, &u)
	return &u, err
}

// json.Unmarshal on type with Unmarshaler — allowed.
func goodJSONUnmarshalWithUnmarshaler(data []byte) (*model.ValidatedModel, error) {
	var v model.ValidatedModel // want `use NewValidatedModel instead of zero-value var for model\.ValidatedModel`
	err := json.Unmarshal(data, &v)
	return &v, err
}

func goodJSONUnmarshalUnrelated(data []byte) (*unrelated.Other, error) {
	var o unrelated.Other
	err := json.Unmarshal(data, &o)
	return &o, err
}

// json.NewDecoder().Decode() on type with Unmarshaler — allowed.
func goodJSONDecoderDecodeWithUnmarshaler(data []byte) (*model.ValidatedModel, error) {
	var v model.ValidatedModel // want `use NewValidatedModel instead of zero-value var for model\.ValidatedModel`
	err := json.NewDecoder(bytes.NewReader(data)).Decode(&v)
	return &v, err
}
