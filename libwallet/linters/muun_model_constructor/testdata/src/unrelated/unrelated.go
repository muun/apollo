// Package unrelated provides a type outside of any model package. Struct literals for these types
// should never be flagged by the linter.
package unrelated

type Other struct {
	X int
}
