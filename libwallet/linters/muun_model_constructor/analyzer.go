package muun_model_constructor

import (
	"go/ast"
	"go/token"
	"go/types"
	"strings"
	"unicode"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"
	"golang.org/x/tools/go/analysis/passes/inspect"
	"golang.org/x/tools/go/ast/inspector"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_model_constructor", newPlugin)
}

func newPlugin(any) (register.LinterPlugin, error) {
	return &plugin{}, nil
}

type plugin struct{}

func (p *plugin) BuildAnalyzers() ([]*analysis.Analyzer, error) {
	return []*analysis.Analyzer{Analyzer}, nil
}

func (p *plugin) GetLoadMode() string {
	return register.LoadModeTypesInfo
}

var Analyzer = newAnalyzer("/domain/model/")

// constructorPanicsFact is exported for model types whose canonical constructor contains a panic.
type constructorPanicsFact struct{}

func (*constructorPanicsFact) AFact()         {}
func (*constructorPanicsFact) String() string { return "constructor panics" }

func newAnalyzer(packagePattern string) *analysis.Analyzer {
	a := &analysis.Analyzer{
		Name:      "muun_model_constructor",
		Doc:       "Checks that structs from model packages use constructors.",
		Requires:  []*analysis.Analyzer{inspect.Analyzer},
		FactTypes: []analysis.Fact{(*constructorPanicsFact)(nil)},
	}
	a.Run = func(pass *analysis.Pass) (any, error) {
		return run(pass, packagePattern)
	}
	return a
}

func run(pass *analysis.Pass, packagePattern string) (any, error) {
	linters.ReportStalenessOnce(pass)

	if strings.Contains(pass.Pkg.Path(), packagePattern) {
		exportConstructorFacts(pass)
	}

	insp := pass.ResultOf[inspect.Analyzer].(*inspector.Inspector)

	nodeFilter := []ast.Node{
		(*ast.CompositeLit)(nil),
		(*ast.GenDecl)(nil),
		(*ast.CallExpr)(nil),
		(*ast.FuncDecl)(nil),
	}

	insp.WithStack(nodeFilter, func(n ast.Node, push bool, stack []ast.Node) bool {
		if !push {
			return true
		}
		switch node := n.(type) {
		case *ast.CompositeLit:
			checkCompositeLit(pass, node, stack, packagePattern)
		case *ast.GenDecl:
			checkVarDecl(pass, node, stack, packagePattern)
		}
		return true
	})

	return nil, nil
}

// exportConstructorFacts marks model types whose canonical constructor panics, so that
// cross-package json.Unmarshal checks can detect when UnmarshalJSON is required.
func exportConstructorFacts(pass *analysis.Pass) {
	for _, name := range pass.Pkg.Scope().Names() {
		obj, ok := pass.Pkg.Scope().Lookup(name).(*types.TypeName)
		if !ok {
			continue
		}
		if _, ok := obj.Type().(*types.Named); !ok {
			continue
		}
		if _, ok := obj.Type().Underlying().(*types.Struct); !ok {
			continue
		}
		ctorName := canonicalConstructorName(obj.Name())
		if funcDeclPanics(pass, ctorName) {
			pass.ExportObjectFact(obj, &constructorPanicsFact{})
		}
	}
}

func funcDeclPanics(pass *analysis.Pass, funcName string) bool {
	for _, file := range pass.Files {
		for _, decl := range file.Decls {
			fd, ok := decl.(*ast.FuncDecl)
			if !ok || fd.Name.Name != funcName || fd.Recv != nil {
				continue
			}
			return containsPanic(pass, fd.Body)
		}
	}
	return false
}

// containsPanic returns true if the AST contains a direct panic() call or a call to any
// function in the preconditions package (which unconditionally panic on failure).
func containsPanic(pass *analysis.Pass, node ast.Node) bool {
	if node == nil {
		return false
	}
	found := false
	ast.Inspect(node, func(n ast.Node) bool {
		if found {
			return false
		}
		call, ok := n.(*ast.CallExpr)
		if !ok {
			return true
		}
		if ident, ok := call.Fun.(*ast.Ident); ok && ident.Name == "panic" {
			found = true
		}
		if sel, ok := call.Fun.(*ast.SelectorExpr); ok {
			obj := pass.TypesInfo.ObjectOf(sel.Sel)
			if obj != nil && obj.Pkg() != nil &&
				obj.Pkg().Name() == "preconditions" {
				found = true
			}
		}
		return !found
	})
	return found
}

func checkCompositeLit(
	pass *analysis.Pass,
	cl *ast.CompositeLit,
	stack []ast.Node,
	packagePattern string,
) {
	typ := pass.TypesInfo.TypeOf(cl)
	if typ == nil {
		return
	}

	named := namedType(typ)
	if named == nil {
		return
	}

	obj, pkgName := modelStructObj(named, packagePattern)
	if obj == nil {
		return
	}

	structName := obj.Name()
	if isConstructor(enclosingFuncName(stack), structName) {
		return
	}

	pass.Reportf(cl.Pos(), "use %s instead of struct literal for %s.%s",
		constructorHint(obj), pkgName, structName)
}

func checkVarDecl(
	pass *analysis.Pass,
	gd *ast.GenDecl,
	stack []ast.Node,
	packagePattern string,
) {
	if gd.Tok != token.VAR {
		return
	}

	for _, spec := range gd.Specs {
		vs, ok := spec.(*ast.ValueSpec)
		if !ok || vs.Type == nil {
			continue
		}

		if vs.Values != nil {
			continue
		}

		typ := pass.TypesInfo.TypeOf(vs.Type)
		if typ == nil {
			continue
		}

		if _, isPtr := typ.(*types.Pointer); isPtr {
			continue
		}

		named := namedType(typ)
		if named == nil {
			continue
		}

		obj, pkgName := modelStructObj(named, packagePattern)
		if obj == nil {
			continue
		}

		structName := obj.Name()
		if isConstructor(enclosingFuncName(stack), structName) {
			continue
		}

		pass.Reportf(vs.Pos(),
			"use %s instead of zero-value var for %s.%s",
			constructorHint(obj), pkgName, structName)
	}
}

func modelStructObj(named *types.Named, packagePattern string) (*types.TypeName, string) {
	if _, ok := named.Underlying().(*types.Struct); !ok {
		return nil, ""
	}
	obj := named.Obj()
	if obj.Pkg() == nil {
		return nil, ""
	}
	if !strings.Contains(obj.Pkg().Path(), packagePattern) {
		return nil, ""
	}
	if obj.Parent() != obj.Pkg().Scope() {
		return nil, ""
	}
	return obj, obj.Pkg().Name()
}

func canonicalConstructorName(structName string) string {
	if ast.IsExported(structName) {
		return "New" + structName
	}
	runes := []rune(structName)
	runes[0] = unicode.ToUpper(runes[0])
	return "new" + string(runes)
}

func constructorHint(obj *types.TypeName) string {
	name := canonicalConstructorName(obj.Name())
	if obj.Pkg().Scope().Lookup(name) != nil {
		return name
	}
	return "a constructor like " + name
}

func isConstructor(funcName, structName string) bool {
	return funcName == canonicalConstructorName(structName)
}

func enclosingFuncName(stack []ast.Node) string {
	for i := len(stack) - 1; i >= 0; i-- {
		if fd, ok := stack[i].(*ast.FuncDecl); ok {
			return fd.Name.Name
		}
	}
	return ""
}

func namedType(typ types.Type) *types.Named {
	if ptr, ok := typ.(*types.Pointer); ok {
		typ = ptr.Elem()
	}
	named, _ := typ.(*types.Named)
	return named
}
