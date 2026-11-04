package muun_subsystem_boundary

import (
	"go/token"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	"github.com/golangci/plugin-module-register/register"
	"golang.org/x/tools/go/analysis"

	"github.com/muun/libwallet/linters"
)

func init() {
	register.Plugin("muun_subsystem_boundary", newPlugin)
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

const subsystemsPrefix = "github.com/muun/libwallet/subsystems/"

var allowedChildren = map[string]bool{
	"internal":     true,
	"presentation": true,
}

var allowedInternalChildren = map[string]bool{
	"data":         true,
	"domain":       true,
	"presentation": true,
	"test":         true,
}

var allowedRootFiles = map[string]bool{
	"wiring.go": true,
}

var Analyzer = &analysis.Analyzer{
	Name: "muun_subsystem_boundary",
	Doc: "Enforces subsystem boundary rules: allowed directory " +
		"structure and cross-subsystem imports through presentation only.",
	Run: run,
}

func run(pass *analysis.Pass) (any, error) {
	linters.ReportStalenessOnce(pass)

	pkgPath := pass.Pkg.Path()
	if !strings.HasPrefix(pkgPath, subsystemsPrefix) {
		return nil, nil
	}

	subsystem := subsystemName(pkgPath)
	if subsystem == "" {
		return nil, nil
	}

	checkStructure(pass, subsystem)
	checkImports(pass, subsystem)

	return nil, nil
}

// subsystemName extracts the subsystem name from a package path.
// "github.com/muun/libwallet/subsystems/recovery/domain/action" -> "recovery"
func subsystemName(pkgPath string) string {
	rest := strings.TrimPrefix(pkgPath, subsystemsPrefix)
	name, _, _ := strings.Cut(rest, "/")
	return name
}

func checkImports(pass *analysis.Pass, currentSubsystem string) {
	for _, file := range pass.Files {
		fileName := filepath.Base(pass.Fset.Position(file.Package).Filename)
		isTest := strings.HasSuffix(fileName, "_test.go")

		for _, imp := range file.Imports {
			path, err := strconv.Unquote(imp.Path.Value)
			if err != nil {
				continue
			}

			if !strings.HasPrefix(path, subsystemsPrefix) {
				continue
			}

			targetSubsystem := subsystemName(path)
			if targetSubsystem == currentSubsystem {
				continue
			}

			targetRest := strings.TrimPrefix(
				path,
				subsystemsPrefix+targetSubsystem+"/",
			)

			if strings.HasPrefix(targetRest, "presentation") {
				continue
			}

			if isTest {
				continue
			}

			pass.Reportf(
				imp.Pos(),
				"cross-subsystem import: %s imports %s's "+
					"internal package %q; use %s/presentation instead",
				currentSubsystem,
				targetSubsystem,
				path,
				targetSubsystem,
			)
		}
	}
}

var structureChecked sync.Map

func checkStructure(pass *analysis.Pass, subsystem string) {
	// Only a package with a hand-written file can report the structure: a report
	// in a generated file is dropped, and no other package reports it again.
	file := linters.FirstHandWrittenFile(pass)
	if file == nil {
		return
	}

	if _, loaded := structureChecked.LoadOrStore(subsystem, true); loaded {
		return
	}

	subsystemDir := findSubsystemDir(pass, subsystem)
	if subsystemDir == "" {
		return
	}

	entries, err := os.ReadDir(subsystemDir)
	if err != nil {
		return
	}

	for _, entry := range entries {
		name := entry.Name()

		if entry.IsDir() {
			if !allowedChildren[name] {
				pass.Reportf(
					file.Package,
					"subsystem %s: directory %q is not allowed "+
						"at the subsystem root; only %v are permitted",
					subsystem,
					name,
					sortedKeys(allowedChildren),
				)
			}
			if name == "internal" {
				checkInternalChildren(
					pass, file.Package, subsystem, filepath.Join(subsystemDir, name),
				)
			}
			continue
		}

		if strings.HasSuffix(name, ".go") && !allowedRootFiles[name] {
			pass.Reportf(
				file.Package,
				"subsystem %s: Go file %q at the subsystem root "+
					"is not allowed; only wiring.go is permitted",
				subsystem,
				name,
			)
		}
	}
}

func checkInternalChildren(
	pass *analysis.Pass, pos token.Pos, subsystem string, internalDir string,
) {
	entries, err := os.ReadDir(internalDir)
	if err != nil {
		return
	}

	for _, entry := range entries {
		name := entry.Name()
		if entry.IsDir() {
			if !allowedInternalChildren[name] {
				pass.Reportf(
					pos,
					"subsystem %s: directory %q is not allowed "+
						"under internal/; only %v are permitted",
					subsystem,
					name,
					sortedKeys(allowedInternalChildren),
				)
			}
			continue
		}
		if strings.HasSuffix(name, ".go") {
			pass.Reportf(
				pos,
				"subsystem %s: Go file %q directly under "+
					"internal/ is not allowed; code belongs "+
					"in one of %v",
				subsystem,
				name,
				sortedKeys(allowedInternalChildren),
			)
		}
	}
}

func findSubsystemDir(pass *analysis.Pass, subsystem string) string {
	if len(pass.Files) == 0 {
		return ""
	}
	filePath := pass.Fset.Position(pass.Files[0].Package).Filename
	subsystemSegment := string(filepath.Separator) + "subsystems" +
		string(filepath.Separator) + subsystem +
		string(filepath.Separator)
	idx := strings.Index(filePath, subsystemSegment)
	if idx < 0 {
		return ""
	}
	return filePath[:idx+len(subsystemSegment)-1]
}

func sortedKeys(set map[string]bool) []string {
	result := make([]string, 0, len(set))
	for k := range set {
		result = append(result, k)
	}
	// deterministic order
	for i := 0; i < len(result); i++ {
		for j := i + 1; j < len(result); j++ {
			if result[i] > result[j] {
				result[i], result[j] = result[j], result[i]
			}
		}
	}
	return result
}
