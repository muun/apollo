package bad

type Multi struct{}

type Extra struct{} // want `one exported struct per file: move Extra to extra.go, or mark it as support for this file's struct with a justified nolint`
