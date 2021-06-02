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

mkdir -p "$(dirname $libwallet)"

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

GOMODCACHE="$GOMODCACHE" \
    go run golang.org/x/mobile/cmd/gomobile bind \
    -target="android" -o "$libwallet" \
    -cache  "$GOCACHE"\
    -trimpath -ldflags="-buildid=. -v" \
    .

st=$?
echo "rebuilt gomobile with status $? to $libwallet"
exit $st
