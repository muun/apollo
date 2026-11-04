// Package muun_one_struct enforces the one-struct-per-file rule from libwallet/CLAUDE.md:
// a file holds one exported struct and is named after it, so the file tree alone is enough
// to find any type. In model packages the rule is strict: any second struct definition is a
// violation. Exported support structs are the rare, justified exception, marked with
// //nolint:muun_one_struct and a reason.
//
// Files named errors.go are exempt: the per-package errors file is an established
// convention and may hold several error types. Test files are exempt too — shared test
// support has its own home (the sub-system's internal/test package, which this analyzer
// does check, since its files are regular Go files).
package muun_one_struct

import (
	"go/ast"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_one_struct", newPlugin)
}

func newPlugin(any) (register.LinterPlugin, error) {
	return &plugin{}, nil
}

type plugin struct{}

func (p *plugin) BuildAnalyzers() ([]*analysis.Analyzer, error) {
	return []*analysis.Analyzer{Analyzer}, nil
}

func (p *plugin) GetLoadMode() string {
	return register.LoadModeSyntax
}

// Analyzer checks packages under the new sub-system tree. Model packages (under
// domain/model) get the strict variant.
var Analyzer = newAnalyzer("/subsystems/", "/domain/model")

func newAnalyzer(scopePattern string, modelPattern string) *analysis.Analyzer {
	a := &analysis.Analyzer{
		Name: "muun_one_struct",
		Doc:  "Checks that files define a single (exported) struct and are named after it.",
	}
	a.Run = func(pass *analysis.Pass) (any, error) {
		return run(pass, scopePattern, modelPattern)
	}
	return a
}

func run(pass *analysis.Pass, scopePattern string, modelPattern string) (any, error) {
	linters.ReportStalenessOnce(pass)

	if !strings.Contains(pass.Pkg.Path(), scopePattern) {
		return nil, nil
	}
	isModelPackage := strings.Contains(pass.Pkg.Path(), modelPattern)

	for _, file := range pass.Files {
		fileName := filepath.Base(pass.Fset.Position(file.Package).Filename)
		if isExemptFile(fileName, file) {
			continue
		}
		checkFile(pass, file, strings.TrimSuffix(fileName, ".go"), isModelPackage)
	}

	return nil, nil
}

func isExemptFile(fileName string, file *ast.File) bool {
	if strings.HasSuffix(fileName, "_test.go") {
		return true
	}
	if fileName == "errors.go" {
		return true
	}
	return ast.IsGenerated(file)
}

func checkFile(pass *analysis.Pass, file *ast.File, baseName string, isModelPackage bool) {
	structs := topLevelStructs(file)
	if len(structs) == 0 {
		return
	}

	var exported []*ast.TypeSpec
	for _, spec := range structs {
		if spec.Name.IsExported() {
			exported = append(exported, spec)
		}
	}

	// Naming: some exported struct must be the file's namesake.
	if len(exported) > 0 && namesakeOf(exported, baseName) == nil {
		pass.Reportf(exported[0].Pos(),
			"file %s.go must be named after its struct: want %s.go",
			baseName, snakeCase(exported[0].Name.Name))
	}

	if isModelPackage {
		// Strict: one struct definition per model file, support structs included.
		for _, spec := range structs {
			if snakeCase(spec.Name.Name) == baseName {
				continue
			}
			pass.Reportf(spec.Pos(),
				"model files hold a single struct definition: move %s to %s.go",
				spec.Name.Name, snakeCase(spec.Name.Name))
		}
		return
	}

	// Everywhere else: one exported struct per file; unexported structs are support.
	if len(exported) > 1 {
		for _, spec := range exported {
			if snakeCase(spec.Name.Name) == baseName {
				continue
			}
			pass.Reportf(spec.Pos(),
				"one exported struct per file: move %s to %s.go, or mark it as "+
					"support for this file's struct with a justified nolint",
				spec.Name.Name, snakeCase(spec.Name.Name))
		}
	}
}

func topLevelStructs(file *ast.File) []*ast.TypeSpec {
	var structs []*ast.TypeSpec
	for _, decl := range file.Decls {
		genDecl, ok := decl.(*ast.GenDecl)
		if !ok {
			continue
		}
		for _, spec := range genDecl.Specs {
			typeSpec, ok := spec.(*ast.TypeSpec)
			if !ok {
				continue
			}
			if _, ok := typeSpec.Type.(*ast.StructType); ok {
				structs = append(structs, typeSpec)
			}
		}
	}
	return structs
}

func namesakeOf(specs []*ast.TypeSpec, baseName string) *ast.TypeSpec {
	for _, spec := range specs {
		if snakeCase(spec.Name.Name) == baseName {
			return spec
		}
	}
	return nil
}

// snakeCase converts a Go identifier to its snake_case file name form, keeping acronym
// runs together: TxSet -> tx_set, HTLCBatch -> htlc_batch, KvHTLCBatchRepository ->
// kv_htlc_batch_repository.
func snakeCase(name string) string {
	runes := []rune(name)
	var out strings.Builder
	for i, r := range runes {
		if unicode.IsUpper(r) && i > 0 {
			previousIsLower := !unicode.IsUpper(runes[i-1])
			nextIsLower := i+1 < len(runes) && !unicode.IsUpper(runes[i+1])
			if previousIsLower || nextIsLower {
				out.WriteRune('_')
			}
		}
		out.WriteRune(unicode.ToLower(r))
	}
	return out.String()
}
