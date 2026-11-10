package testenv

import (
	"bufio"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"github.com/go-errors/errors"
)

// LoadEnvironmentIntoMap builds a string map of environment variables.
// It seeds the map from os.Environ, and then loads the env vars found on each given file path
// in order.
// File paths are resolved relative to the repository root. Missing files are ignored.
func LoadEnvironmentIntoMap(envVarsFilePaths ...string) (map[string]string, error) {
	env := make(map[string]string)

	loadEnvVarsFromEnv(env)

	root, err := findRepoRoot()
	if err != nil {
		return nil, err
	}

	for _, envVarFilePath := range envVarsFilePaths {
		path := filepath.Join(root, envVarFilePath)
		if err := loadEnvVarsFromFile(env, path); err != nil {
			return nil, err
		}
	}

	return env, nil
}

// loadEnvVarsFromEnv copies every entry from os.Environ into the provided env map.
func loadEnvVarsFromEnv(env map[string]string) {
	for _, keyValue := range os.Environ() {
		eq := strings.IndexByte(keyValue, '=')
		if eq <= 0 {
			// skip
			continue
		}

		key := parseEnvKey(keyValue[:eq])
		value := parseEnvValue(keyValue[eq+1:])

		env[key] = value
	}
}

// findRepoRoot walks up from the current working directory and returns the first ancestor that
// contains a .git entry.
func findRepoRoot() (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", errors.Errorf("getwd: %w", err)
	}

	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir, nil
		}

		parent := filepath.Dir(dir)
		if parent == dir {
			return "", errors.Errorf("repo root not found from %s", dir)
		}
		dir = parent
	}
}

// loadEnvVarsFromFile load env vars from envVarsFilePath and loads them into env.
// A missing file is not an error.
func loadEnvVarsFromFile(env map[string]string, envVarsFilePath string) error {
	file, err := os.Open(envVarsFilePath)
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		return errors.Errorf("open %s: %w", envVarsFilePath, err)
	}
	defer func() { _ = file.Close() }()

	scanner := bufio.NewScanner(file)
	lineNum := 0
	for scanner.Scan() {
		lineNum++
		key, value, err := parseEnvLine(scanner.Text())
		if err != nil {
			return errors.Errorf("%s:%d: %w", envVarsFilePath, lineNum, err)
		}
		if key == "" {
			continue
		}

		env[key] = value
	}
	if err := scanner.Err(); err != nil {
		return errors.Errorf("read %s: %w", envVarsFilePath, err)
	}

	return nil
}

// parseEnvLine parses one line of an env file.
// Blank lines and lines starting with '#' return an empty key.
// Values can be singly or doubly quoted.
func parseEnvLine(line string) (string, string, error) {
	trimmed := strings.TrimSpace(line)
	if trimmed == "" || strings.HasPrefix(trimmed, "#") {
		return "", "", nil
	}

	eq := strings.IndexByte(trimmed, '=')
	if eq <= 0 {
		return "", "", errors.Errorf("invalid env line: %q", line)
	}

	key := parseEnvKey(trimmed[:eq])
	value := parseEnvValue(trimmed[eq+1:])
	return key, value, nil
}

func parseEnvKey(raw string) string {
	return strings.TrimSpace(raw)
}

func parseEnvValue(raw string) string {
	raw = strings.TrimSpace(raw)
	if len(raw) >= 2 {
		first, last := raw[0], raw[len(raw)-1]
		if (first == '"' && last == '"') || (first == '\'' && last == '\'') {
			raw = raw[1 : len(raw)-1]
		}
	}
	return raw
}
