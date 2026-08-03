package muun_errorf_wrap

import (
	"go/ast"
	"go/constant"
	"go/types"
	"strings"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"
	"golang.org/x/tools/go/analysis/passes/inspect"
	"golang.org/x/tools/go/ast/inspector"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_errorf_wrap", newPlugin)
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

var Analyzer = &analysis.Analyzer{
	Name:     "muun_errorf_wrap",
	Doc:      "Checks that errors.Errorf from go-errors uses %w for error arguments.",
	Requires: []*analysis.Analyzer{inspect.Analyzer},
	Run:      run,
}

const goErrorsPkg = "github.com/go-errors/errors"

var errorIface = types.Universe.Lookup("error").Type().Underlying().(*types.Interface)

func run(pass *analysis.Pass) (any, error) {
	linters.ReportStalenessOnce(pass)

	insp := pass.ResultOf[inspect.Analyzer].(*inspector.Inspector)

	nodeFilter := []ast.Node{(*ast.CallExpr)(nil)}

	insp.Preorder(nodeFilter, func(n ast.Node) {
		call := n.(*ast.CallExpr)
		checkErrorfCall(pass, call)
	})

	return nil, nil
}

func checkErrorfCall(pass *analysis.Pass, call *ast.CallExpr) {
	sel, ok := call.Fun.(*ast.SelectorExpr)
	if !ok || sel.Sel.Name != "Errorf" {
		return
	}

	obj := pass.TypesInfo.ObjectOf(sel.Sel)
	if obj == nil || obj.Pkg() == nil {
		return
	}

	if obj.Pkg().Path() != goErrorsPkg {
		return
	}

	if len(call.Args) < 2 {
		return
	}

	formatArg := call.Args[0]
	variadicArgs := call.Args[1:]

	formatVal := pass.TypesInfo.Types[formatArg].Value
	if formatVal == nil || formatVal.Kind() != constant.String {
		return
	}
	format := constant.StringVal(formatVal)

	verbs := parseFormatVerbs(format)

	for _, v := range verbs {
		if v.argIndex >= len(variadicArgs) {
			break
		}

		argType := pass.TypesInfo.TypeOf(variadicArgs[v.argIndex])
		if argType == nil {
			continue
		}

		if !types.Implements(argType, errorIface) {
			continue
		}

		// Only %v and %s render the error's message; other verbs (%T, %p, ...)
		// are legitimate non-wrapping ways to format an error.
		if v.verb == 'v' || v.verb == 's' {
			pass.Reportf(call.Pos(),
				"errors.Errorf call has %%%c for error argument; use %%w to wrap it",
				v.verb)
		}
	}
}

type formatVerb struct {
	argIndex int
	verb     byte
}

func parseFormatVerbs(format string) []formatVerb {
	var verbs []formatVerb
	argIndex := 0
	i := 0
	for i < len(format) {
		if format[i] != '%' {
			i++
			continue
		}
		i++
		if i >= len(format) {
			break
		}
		if format[i] == '%' {
			i++
			continue
		}
		// skip flags: #, 0, -, +, ' '
		for i < len(format) && strings.ContainsRune("#0-+ ", rune(format[i])) {
			i++
		}
		// skip width (* consumes an argument)
		if i < len(format) && format[i] == '*' {
			argIndex++
			i++
		} else {
			for i < len(format) && format[i] >= '0' && format[i] <= '9' {
				i++
			}
		}
		// skip precision
		if i < len(format) && format[i] == '.' {
			i++
			if i < len(format) && format[i] == '*' {
				argIndex++
				i++
			} else {
				for i < len(format) && format[i] >= '0' && format[i] <= '9' {
					i++
				}
			}
		}
		if i >= len(format) {
			break
		}
		verbs = append(verbs, formatVerb{argIndex: argIndex, verb: format[i]})
		argIndex++
		i++
	}
	return verbs
}
