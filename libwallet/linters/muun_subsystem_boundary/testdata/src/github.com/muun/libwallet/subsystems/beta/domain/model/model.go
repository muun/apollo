// beta keeps domain/ at its root so that alpha can import a non-presentation package of
// it: Go itself rejects a cross-package internal/ import, which never reaches the linter.
package model // want `directory "domain" is not allowed at the subsystem root` `Go file "extra.go" at the subsystem root is not allowed` `Go file "stray.go" directly under internal/ is not allowed` `directory "util" is not allowed under internal/`

const Suffix = "!"
