#!/bin/bash

set -e

repo_root=$(git rev-parse --show-toplevel)
build_dir="$repo_root/libwallet/.build"

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

GOCACHE="$build_dir/android"

# Install and setup gomobile on demand (no-op if already installed and up-to-date)
. "$repo_root/tools/bootstrap-gomobile.sh"

# gomobile bind generates the src-android-* directories several times, leading to fail with:
# /tmp/go-build3034672677/b001/exe/gomobile: mkdir $GOCACHE/src-android-arm64: file exists
# exit status 1
# There is no significant change in build times without these folders.
rm -rf "$GOCACHE"/src-android-* 2>/dev/null \
  || echo "No src-android-* directories found in GOCACHE."

# Finally run gomobile bind using the version pinned by the go.mod file.
# We need -androidapi 19 to set the min api targeted by the NDK.
# The -trimpath and -ldflags are passed on to go build and are part of keeping the build reproducible.
# Note that we bind & build two packages top-level libwallet and newop.
go run golang.org/x/mobile/cmd/gomobile bind \
    -target="android" -o "$libwallet" \
    -androidapi 19 \
    -trimpath -ldflags="-buildid=. -v" \
    . ./newop

st=$?
echo "rebuilt gomobile with status $? to $libwallet"
exit $st
