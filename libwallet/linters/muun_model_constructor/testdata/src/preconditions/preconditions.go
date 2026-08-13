package preconditions

func CheckState(expr bool) {
	if !expr {
		panic("precondition failed")
	}
}
