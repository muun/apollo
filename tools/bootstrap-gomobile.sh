#!/bin/bash

repo_root=$(git rev-parse --show-toplevel)
build_dir="$repo_root/libwallet/.build"

cd "$repo_root/libwallet"

mkdir -p "$build_dir/pkg"

# Use a shared dependency cache by setting GOMODCACHE

GOMODCACHE="$build_dir/pkg" \
    go install golang.org/x/mobile/cmd/gomobile && \
    go install golang.org/x/mobile/cmd/gobind
