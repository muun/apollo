#!/bin/bash

set -e

repo_root=$(git rev-parse --show-toplevel)
build_dir="$repo_root/libwallet/.build"

# Install and setup gomobile on demand (no-op if already installed and up-to-date)
. "$repo_root/tools/bootstrap-gomobile.sh"

# OSS project has a different folder libwallet aar, so we receive it as param
libwallet="$1"
if [[ ! -s "$1" ]]; then
    libwallet="$repo_root/android/libwallet/libs/libwallet.aar"
fi

cd "$repo_root/libwallet"

mkdir -p "$(dirname "$libwallet")"

# Create the cache folders
mkdir -p "$build_dir/android"
mkdir -p "$build_dir/pkg"

# Line by line explanation
# 1. Use a shared dependency cache between iOS and Android by setting GOMODCACHE
# 2. Run gomobile bind using the version pinned by the go.mod file
# 3. Set output flags
# 4. Use a fixed build cache location
# 5. Opt in to reproducible builds

if [[ -z $GOMODCACHE ]]; then
    GOMODCACHE="$build_dir/pkg"
fi

if [[ -z $GOCACHE ]]; then
    GOCACHE="$build_dir/android"
fi

# gomobile bind generates the src-android-* directories several times, leading to fail with:
# /tmp/go-build3034672677/b001/exe/gomobile: mkdir $GOCACHE/src-android-arm64: file exists
# exit status 1
# There is no significant change in build times without these folders.
rm -rf "$GOCACHE"/src-android-* 2>/dev/null \
  || echo "No src-android-* directories found in GOCACHE."

GOMODCACHE="$GOMODCACHE" \
    go run golang.org/x/mobile/cmd/gomobile bind \
    -target="android" -o "$libwallet" \
    -androidapi 19 \
    -trimpath -ldflags="-buildid=. -v" \
    . ./newop

st=$?
echo "rebuilt gomobile with status $? to $libwallet"
exit $st
