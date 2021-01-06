#!/bin/bash

set -e

repo_root=$(git rev-parse --show-toplevel)
build_dir="$repo_root/libwallet/.build"

# OSS project has a different folder libwallet aar, so we receive it as param
libwallet="$1"
if [[ ! -s "$1" ]]; then
    libwallet="$repo_root/android/apollo/libs/libwallet.aar"
fi


cd "$repo_root/libwallet"

# Create the cache folders
mkdir -p "$build_dir/android"
mkdir -p "$build_dir/pkg"

# Use a shared dependency cache between iOS and Android by setting GOMODCACHE

GOMODCACHE="$build_dir/pkg" \
    go run golang.org/x/mobile/cmd/gomobile bind -target=android -o "$libwallet" -cache "$build_dir/android" .

st=$?
echo "rebuilt gomobile with status $? to $libwallet"
exit $st
