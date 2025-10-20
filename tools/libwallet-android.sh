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

# Set linker flags for 16KB page alignment required by Android targetSdk 35+
# CGO_LDFLAGS: Passes flags to the C linker when building Go code with CGO
# LDFLAGS: General linker flags that may be used by the build system
# Both are set to ensure compatibility across different build scenarios
#
# -Wl,-z,max-page-size=16384: Sets maximum page size to 16KB (16384 bytes)
# This defines the largest page size the linker can use for memory alignment
# Required for compatibility with Android's new 16KB page size support
#
# -Wl,-z,common-page-size=16384: Sets common page size to 16KB
# This aligns data sections to 16KB boundaries for optimal memory management
# Ensures proper alignment of shared library segments in memory
#
# Android 16KB page size support: https://developer.android.com/guide/practices/page-sizes
# GNU LD linker options: https://sourceware.org/binutils/docs/ld/Options.html
# CGO_LDFLAGS documentation: https://pkg.go.dev/cmd/cgo
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

# Finally run gomobile bind using the version pinned by the go.mod file.
# We need -androidapi 19 to set the min api targeted by the NDK.
# The -trimpath and -ldflags are passed on to go build and are part of keeping the build reproducible.
# Note that we bind & build two packages top-level libwallet and newop.
go run golang.org/x/mobile/cmd/gomobile bind \
    -target="android" -o "$libwallet" \
    -androidapi 19 \
    -trimpath -ldflags="-buildid=. -v" \
    . ./newop ./app_provided_data ./libwallet_init

st=$?

echo ""
echo "rebuilt gomobile with status $? to $libwallet"
exit $st
