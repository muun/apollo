package linters

import (
	"go/ast"

	"golang.org/x/tools/go/analysis"
)

// FirstHandWrittenFile returns the package's first file that is not generated,
// or nil if every file is. golangci-lint drops reports in generated files, so a
// diagnostic reported only once must go on this file.
func FirstHandWrittenFile(pass *analysis.Pass) *ast.File {
	for _, file := range pass.Files {
		if !ast.IsGenerated(file) {
			return file
		}
	}
	return nil
}
