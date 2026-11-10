// Package muun_struct_order enforces the in-file ordering rule from libwallet/CLAUDE.md:
// given a struct, its file block is definition, compile-time interface assertions,
// constructors, exported methods, unexported methods — in that order, with nothing else
// mixed in. Constants or support types the methods need go before or after the block. In
// an allowed multiple-struct file, each struct forms its own contiguous block and the
// file's namesake struct comes first.
package muun_struct_order

import (
	"go/ast"
	"go/token"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_struct_order", newPlugin)
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

// Analyzer checks packages under the new sub-system tree.
var Analyzer = newAnalyzer("/subsystems/")

func newAnalyzer(scopePattern string) *analysis.Analyzer {
	a := &analysis.Analyzer{
		Name: "muun_struct_order",
		Doc:  "Checks the definition/assertions/constructors/public/private order of struct files.",
	}
	a.Run = func(pass *analysis.Pass) (any, error) {
		return run(pass, scopePattern)
	}
	return a
}

// itemKind ranks a declaration's place inside a struct's block. The block must be
// contiguous and its kinds non-decreasing.
type itemKind int

const (
	kindDefinition itemKind = iota
	kindAssertion
	kindConstructor
	kindExportedMethod
	kindUnexportedMethod
)

var kindNames = map[itemKind]string{
	kindDefinition:       "the definition",
	kindAssertion:        "interface assertions",
	kindConstructor:      "constructors",
	kindExportedMethod:   "exported methods",
	kindUnexportedMethod: "unexported methods",
}

// item is one top-level declaration, attributed to a struct when it belongs to its block.
type item struct {
	pos        token.Pos
	end        token.Pos
	kind       itemKind
	structName string // empty for declarations outside every block
	node       ast.Node
}

func run(pass *analysis.Pass, scopePattern string) (any, error) {
	linters.ReportStalenessOnce(pass)

	if !strings.Contains(pass.Pkg.Path(), scopePattern) {
		return nil, nil
	}

	for _, file := range pass.Files {
		fileName := filepath.Base(pass.Fset.Position(file.Package).Filename)
		if strings.HasSuffix(fileName, "_test.go") || ast.IsGenerated(file) {
			continue
		}
		checkFile(pass, file, strings.TrimSuffix(fileName, ".go"))
	}

	return nil, nil
}

func checkFile(pass *analysis.Pass, file *ast.File, baseName string) {
	structNames := structNamesOf(file)
	if len(structNames) == 0 {
		return
	}

	items := classifyDecls(file, structNames)

	checkNamesakeFirst(pass, items, baseName)

	for structName := range structNames {
		checkBlock(pass, items, structName)
	}
}

// checkBlock verifies that the struct's declarations are contiguous and in order.
func checkBlock(pass *analysis.Pass, items []item, structName string) {
	first, last := -1, -1
	for i, current := range items {
		if current.structName == structName {
			if first == -1 {
				first = i
			}
			last = i
		}
	}
	if first == -1 || first == last {
		return
	}

	previousKind := kindDefinition
	for _, current := range items[first : last+1] {
		if current.structName != structName {
			pass.Reportf(current.pos,
				"declaration interleaved in the %s block: move it before or after "+
					"the struct's definition, constructors and methods",
				structName)
			continue
		}

		if current.kind < previousKind {
			pass.Reportf(current.pos,
				"%s of %s must come before %s: order is definition, interface "+
					"assertions, constructors, exported methods, unexported methods",
				kindNames[current.kind], structName, kindNames[previousKind])
			continue
		}
		previousKind = current.kind
	}
}

// checkNamesakeFirst verifies that the struct the file is named after is defined before
// any other struct in the file.
func checkNamesakeFirst(pass *analysis.Pass, items []item, baseName string) {
	var firstDefinition, namesakeDefinition *item
	for i := range items {
		current := &items[i]
		if current.kind != kindDefinition || current.structName == "" {
			continue
		}
		if firstDefinition == nil {
			firstDefinition = current
		}
		if snakeCase(current.structName) == baseName {
			namesakeDefinition = current
		}
	}

	if namesakeDefinition == nil || firstDefinition == namesakeDefinition {
		return
	}
	pass.Reportf(firstDefinition.pos,
		"the file's namesake struct %s must be defined before %s",
		namesakeDefinition.structName, firstDefinition.structName)
}

func classifyDecls(file *ast.File, structNames map[string]bool) []item {
	var items []item
	for _, decl := range file.Decls {
		switch node := decl.(type) {
		case *ast.GenDecl:
			items = append(items, classifyGenDecl(node, structNames)...)
		case *ast.FuncDecl:
			items = append(items, classifyFuncDecl(node, structNames))
		}
	}
	return items
}

func classifyGenDecl(decl *ast.GenDecl, structNames map[string]bool) []item {
	var items []item

	if decl.Tok == token.TYPE {
		for _, spec := range decl.Specs {
			typeSpec, ok := spec.(*ast.TypeSpec)
			if !ok {
				continue
			}
			if _, isStruct := typeSpec.Type.(*ast.StructType); isStruct {
				items = append(items, item{
					pos:        typeSpec.Pos(),
					end:        typeSpec.End(),
					kind:       kindDefinition,
					structName: typeSpec.Name.Name,
					node:       typeSpec,
				})
			}
		}
		if len(items) > 0 {
			return items
		}
	}

	if structName := assertedStruct(decl, structNames); structName != "" {
		return []item{{
			pos:        decl.Pos(),
			end:        decl.End(),
			kind:       kindAssertion,
			structName: structName,
			node:       decl,
		}}
	}

	return []item{{pos: decl.Pos(), end: decl.End(), node: decl}}
}

func classifyFuncDecl(decl *ast.FuncDecl, structNames map[string]bool) item {
	base := item{pos: decl.Pos(), end: decl.End(), node: decl}

	if decl.Recv != nil {
		receiver := receiverStruct(decl, structNames)
		if receiver == "" {
			return base
		}
		kind := kindUnexportedMethod
		if decl.Name.IsExported() {
			kind = kindExportedMethod
		}
		base.kind = kind
		base.structName = receiver
		return base
	}

	if constructed := constructedStruct(decl, structNames); constructed != "" {
		base.kind = kindConstructor
		base.structName = constructed
		return base
	}

	return base
}

// assertedStruct recognizes compile-time interface assertions like
// `var _ Iface = (*Struct)(nil)`, attributing them to the struct they mention.
func assertedStruct(decl *ast.GenDecl, structNames map[string]bool) string {
	if decl.Tok != token.VAR {
		return ""
	}
	for _, spec := range decl.Specs {
		valueSpec, ok := spec.(*ast.ValueSpec)
		if !ok {
			return ""
		}
		for _, name := range valueSpec.Names {
			if name.Name != "_" {
				return ""
			}
		}
		if structName := mentionedStruct(valueSpec, structNames); structName != "" {
			return structName
		}
	}
	return ""
}

// constructedStruct recognizes constructors: `New...`/`new...` functions that either
// return the struct type or build a literal of it. The action pattern (unexported struct,
// constructor returning an exported interface) is covered by the literal check.
func constructedStruct(decl *ast.FuncDecl, structNames map[string]bool) string {
	if !strings.HasPrefix(decl.Name.Name, "New") &&
		!strings.HasPrefix(decl.Name.Name, "new") {
		return ""
	}

	if decl.Type.Results != nil {
		for _, result := range decl.Type.Results.List {
			if structName := identOf(result.Type, structNames); structName != "" {
				return structName
			}
		}
	}

	constructed := ""
	ast.Inspect(decl.Body, func(node ast.Node) bool {
		if constructed != "" {
			return false
		}
		literal, ok := node.(*ast.CompositeLit)
		if !ok {
			return true
		}
		if structName := identOf(literal.Type, structNames); structName != "" {
			constructed = structName
			return false
		}
		return true
	})
	return constructed
}

func receiverStruct(decl *ast.FuncDecl, structNames map[string]bool) string {
	if len(decl.Recv.List) != 1 {
		return ""
	}
	return identOf(decl.Recv.List[0].Type, structNames)
}

// mentionedStruct returns the single file-local struct referenced by the value spec.
func mentionedStruct(spec *ast.ValueSpec, structNames map[string]bool) string {
	mentioned := ""
	ast.Inspect(spec, func(node ast.Node) bool {
		ident, ok := node.(*ast.Ident)
		if !ok || !structNames[ident.Name] {
			return true
		}
		mentioned = ident.Name
		return false
	})
	return mentioned
}

// identOf unwraps pointers, generics and parenthesis down to an identifier naming one of
// the file's structs.
func identOf(expr ast.Expr, structNames map[string]bool) string {
	switch node := expr.(type) {
	case *ast.Ident:
		if structNames[node.Name] {
			return node.Name
		}
	case *ast.StarExpr:
		return identOf(node.X, structNames)
	case *ast.ParenExpr:
		return identOf(node.X, structNames)
	case *ast.IndexExpr:
		return identOf(node.X, structNames)
	}
	return ""
}

func structNamesOf(file *ast.File) map[string]bool {
	names := make(map[string]bool)
	for _, decl := range file.Decls {
		genDecl, ok := decl.(*ast.GenDecl)
		if !ok || genDecl.Tok != token.TYPE {
			continue
		}
		for _, spec := range genDecl.Specs {
			typeSpec, ok := spec.(*ast.TypeSpec)
			if !ok {
				continue
			}
			if _, isStruct := typeSpec.Type.(*ast.StructType); isStruct {
				names[typeSpec.Name.Name] = true
			}
		}
	}
	return names
}

// snakeCase converts a Go identifier to its snake_case file name form, keeping acronym
// runs together. Kept in sync with muun_one_struct.
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
