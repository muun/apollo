package libwallet

type StringList struct {
	elems []string
}

func NewStringList() *StringList {
 	return &StringList{}
}

func newStringList(elems []string) *StringList {
	return &StringList{elems}
}

func (l *StringList) Length() int {
	return len(l.elems)
}

func (l *StringList) Get(index int) string {
	return l.elems[index]
}

func (l *StringList) Add(s string) {
	l.elems = append(l.elems, s)
}

func (l *StringList) Contains(s string) bool {
	for _, v := range l.elems {
		if v == s {
			return true
		}
	}

	return false
}

type IntList struct {
	elems []int
}

func NewIntList() *IntList {
	return &IntList{}
}

func newIntList(elems []int) *IntList {
	return &IntList{elems}
}

func (l *IntList) Length() int {
	return len(l.elems)
}

func (l *IntList) Get(index int) int {
	return l.elems[index]
}

func (l *IntList) Add(number int) {
	l.elems = append(l.elems, number)
}

func (l *IntList) Contains(number int) bool {
	for _, v := range l.elems {
		if v == number {
			return true
		}
	}

	return false
}
