package model

type Thing struct {
	Name string
}

func NewThing(name string) Thing {
	return Thing{Name: name}
}

func (t Thing) String() string {
	return t.Name
}

const DefaultName = "thing"

var Default = Thing{} // want `var declarations are not allowed`

func ToProtoThing(t Thing) string {
	return t.Name
}

func FromProtoThing(name string) Thing {
	return Thing{Name: name}
}

func Build() Thing { // want `func Build is neither`
	return Thing{}
}
