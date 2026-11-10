// Package muun_presentation_purity enforces what a sub-system's presentation packages may
// hold.
//
// The root presentation package is the sub-system's contract: a single file named after the
// sub-system, exporting the interface named after the sub-system, any other interfaces, and
// nothing else. It imports nothing under internal/, so two sub-systems depending on each
// other never form an import cycle.
//
// internal/presentation is the contract's implementation: a single file named after the
// sub-system, exporting the struct named after the sub-system and its constructor, and
// nothing else. Its methods take a pointer receiver, so only the struct's address
// satisfies the contract and libwallet_init can hand it out before wiring assigns it.
//
// presentation/model packages hold the boundary types: only types and constants, with
// constructors and proto mappers as the only free functions. presentation/tests packages are
// not checked.
package muun_presentation_purity

import (
	"go/ast"
	"go/token"
	"path/filepath"
	"slices"
	"strings"
	"unicode"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_presentation_purity", newPlugin)
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

const subsystemsPrefix = "/subsystems/"

var Analyzer = &analysis.Analyzer{
	Name: "muun_presentation_purity",
	Doc: "Checks that presentation/ holds one file exporting only the sub-system's " +
		"interfaces, internal/presentation/ one file exporting only its implementation, " +
		"and presentation/model/ only types, constants, constructors and proto mappers.",
	Run: run,
}

func run(pass *analysis.Pass) (any, error) {
	linters.ReportStalenessOnce(pass)

	pkgPath := pass.Pkg.Path()
	if !strings.Contains(pkgPath, subsystemsPrefix) {
		return nil, nil
	}
	if !strings.Contains(pkgPath, "/presentation") {
		return nil, nil
	}

	isModel := strings.HasSuffix(pkgPath, "/presentation/model") ||
		strings.Contains(pkgPath, "/presentation/model/")
	isTests := strings.HasSuffix(pkgPath, "/presentation/tests") ||
		strings.Contains(pkgPath, "/presentation/tests/")
	isImplementation := strings.HasSuffix(pkgPath, "/internal/presentation")

	if isTests {
		return nil, nil
	}

	subsystem := subsystemName(pkgPath)

	for _, file := range pass.Files {
		fileName := filepath.Base(pass.Fset.Position(file.Package).Filename)
		if strings.HasSuffix(fileName, "_test.go") || ast.IsGenerated(file) {
			continue
		}

		switch {
		case isModel:
			checkModelFile(pass, file)
		case isImplementation:
			checkImplementationFile(pass, file, fileName, subsystem)
		default:
			checkContractFile(pass, file, fileName, subsystem)
		}
	}

	return nil, nil
}

// subsystemName extracts "security_card" from ".../subsystems/security_card/presentation".
func subsystemName(pkgPath string) string {
	_, rest, _ := strings.Cut(pkgPath, subsystemsPrefix)
	name, _, _ := strings.Cut(rest, "/")
	return name
}

// pascalCase turns "security_card" into "SecurityCard".
func pascalCase(name string) string {
	var out strings.Builder
	upper := true
	for _, r := range name {
		if r == '_' {
			upper = true
			continue
		}
		if upper {
			out.WriteRune(unicode.ToUpper(r))
			upper = false
			continue
		}
		out.WriteRune(r)
	}
	return out.String()
}

// checkContractFile ensures the file is the sub-system's single contract file, that it
// exports the sub-system's interface and only interfaces, and that it imports nothing under
// internal/.
func checkContractFile(
	pass *analysis.Pass,
	file *ast.File,
	fileName string,
	subsystem string,
) {
	isContractFile := fileName == subsystem+".go"
	if !isContractFile {
		pass.Reportf(file.Package,
			"presentation holds a single file, named after the sub-system: "+
				"merge %s into %s.go",
			fileName, subsystem)
	}

	interfaceName := pascalCase(subsystem)
	var exportedTypes []string

	for _, imp := range file.Imports {
		path := strings.Trim(imp.Path.Value, `"`)
		if strings.Contains(path, "/internal/") {
			pass.Reportf(imp.Pos(),
				"the sub-system's contract imports nothing under internal/: "+
					"move what needs %q to internal/presentation",
				path)
		}
	}

	for _, decl := range file.Decls {
		switch d := decl.(type) {
		case *ast.GenDecl:
			exportedTypes = append(exportedTypes, checkContractGenDecl(pass, d, interfaceName)...)
		case *ast.FuncDecl:
			if d.Recv != nil || !d.Name.IsExported() {
				continue
			}
			pass.Reportf(d.Pos(),
				"presentation exports only interfaces: "+
					"move func %s to internal/presentation",
				d.Name.Name)
		}
	}

	missesInterface := len(exportedTypes) > 0 && !slices.Contains(exportedTypes, interfaceName)
	if isContractFile && missesInterface {
		pass.Reportf(file.Package,
			"the sub-system's contract declares the %s interface",
			interfaceName)
	}
}

// checkContractGenDecl reports every exported declaration that is not an interface, and
// returns the names of the exported types it declares.
func checkContractGenDecl(
	pass *analysis.Pass,
	decl *ast.GenDecl,
	interfaceName string,
) []string {
	var exportedTypes []string
	switch decl.Tok {
	case token.TYPE:
		for _, spec := range decl.Specs {
			typeSpec := spec.(*ast.TypeSpec)
			if !typeSpec.Name.IsExported() {
				continue
			}
			exportedTypes = append(exportedTypes, typeSpec.Name.Name)
			_, isInterface := typeSpec.Type.(*ast.InterfaceType)
			if typeSpec.Name.Name == interfaceName {
				if !isInterface {
					pass.Reportf(typeSpec.Pos(),
						"the sub-system's contract is an interface: "+
							"%s is not one",
						typeSpec.Name.Name)
				}
				continue
			}
			if !isInterface {
				pass.Reportf(typeSpec.Pos(),
					"presentation exports only interfaces: "+
						"unexport type %s or move it to presentation/model",
					typeSpec.Name.Name)
			}
		}
	case token.VAR, token.CONST:
		checkNoExportedValues(pass, decl, "presentation")
	}
	return exportedTypes
}

// checkImplementationFile ensures the file is the sub-system's single implementation
// file, that it exports only the sub-system's struct and its constructor, and that the
// struct's methods take a pointer receiver.
func checkImplementationFile(
	pass *analysis.Pass,
	file *ast.File,
	fileName string,
	subsystem string,
) {
	if fileName != subsystem+".go" {
		pass.Reportf(file.Package,
			"internal/presentation holds a single file, named after the sub-system: "+
				"merge %s into %s.go",
			fileName, subsystem)
	}

	structName := pascalCase(subsystem)
	constructorName := "New" + structName

	for _, decl := range file.Decls {
		switch d := decl.(type) {
		case *ast.GenDecl:
			checkImplementationGenDecl(pass, d, structName)
		case *ast.FuncDecl:
			if d.Recv != nil {
				checkReceiver(pass, d, structName)
				continue
			}
			if !d.Name.IsExported() || d.Name.Name == constructorName {
				continue
			}
			pass.Reportf(d.Pos(),
				"internal/presentation exports only %s and %s: "+
					"unexport func %s or move it to internal/domain",
				structName, constructorName, d.Name.Name)
		}
	}
}

func checkImplementationGenDecl(
	pass *analysis.Pass,
	decl *ast.GenDecl,
	structName string,
) {
	switch decl.Tok {
	case token.TYPE:
		for _, spec := range decl.Specs {
			typeSpec := spec.(*ast.TypeSpec)
			if !typeSpec.Name.IsExported() {
				continue
			}
			if typeSpec.Name.Name != structName {
				pass.Reportf(typeSpec.Pos(),
					"internal/presentation exports only the %s struct: "+
						"unexport type %s or move it to internal/domain",
					structName, typeSpec.Name.Name)
				continue
			}
			if _, ok := typeSpec.Type.(*ast.StructType); !ok {
				pass.Reportf(typeSpec.Pos(),
					"the sub-system's implementation is a struct: "+
						"%s is not one",
					typeSpec.Name.Name)
			}
		}
	case token.VAR, token.CONST:
		checkNoExportedValues(pass, decl, "internal/presentation")
	}
}

// checkReceiver ensures a method of the implementation struct takes a pointer receiver.
// With a value receiver the struct's value would satisfy the contract too, and a copy of
// it could be handed out before wiring assigns the value.
func checkReceiver(pass *analysis.Pass, method *ast.FuncDecl, structName string) {
	if len(method.Recv.List) != 1 {
		return
	}
	receiver := method.Recv.List[0].Type
	if _, isPointer := receiver.(*ast.StarExpr); isPointer {
		return
	}
	ident, ok := receiver.(*ast.Ident)
	if !ok || ident.Name != structName {
		return
	}
	pass.Reportf(method.Pos(),
		"methods of %s take a pointer receiver, so that only *%s satisfies "+
			"the contract: change %s",
		structName, structName, method.Name.Name)
}

func checkNoExportedValues(pass *analysis.Pass, decl *ast.GenDecl, where string) {
	for _, spec := range decl.Specs {
		valueSpec, ok := spec.(*ast.ValueSpec)
		if !ok {
			continue
		}
		for _, name := range valueSpec.Names {
			if !name.IsExported() {
				continue
			}
			pass.Reportf(name.Pos(),
				"%s declares no exported values: "+
					"move %s to presentation/model",
				where, name.Name)
		}
	}
}

// checkModelFile ensures only type and const declarations exist
// (no free functions, no vars). Constructors are allowed and gated
// separately by muun_model_constructor, and so are proto mappers.
func checkModelFile(pass *analysis.Pass, file *ast.File) {
	for _, decl := range file.Decls {
		switch d := decl.(type) {
		case *ast.GenDecl:
			if d.Tok == token.VAR {
				pass.Reportf(d.Pos(),
					"presentation/model declares only types "+
						"and constants: var declarations "+
						"are not allowed")
			}
		case *ast.FuncDecl:
			if d.Recv != nil {
				// Methods on types are fine.
				continue
			}
			if isConstructor(d) || isProtoMapper(d) {
				continue
			}
			pass.Reportf(d.Pos(),
				"presentation/model declares only types, "+
					"constants, constructors and proto mappers: "+
					"func %s is neither",
				d.Name.Name)
		}
	}
}

func isConstructor(decl *ast.FuncDecl) bool {
	return strings.HasPrefix(decl.Name.Name, "New")
}

func isProtoMapper(decl *ast.FuncDecl) bool {
	return strings.HasPrefix(decl.Name.Name, "ToProto") ||
		strings.HasPrefix(decl.Name.Name, "FromProto")
}
