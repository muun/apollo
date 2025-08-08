#!/bin/bash

set -e

repo_root=$(git rev-parse --show-toplevel)
script_path=$(dirname "$0")
cd "$script_path"

librs_path=$(git rev-parse --show-prefix)

function _cargo() {
    # Allow callers to disable docker via an env var. This is intended for apollos reproducible builds that
    # already run inside docker.
    if [ -n "$USE_HOST_CARGO" ]; then
        cargo "$@"
    else
        docker run -v "$repo_root:/src" -w "/src/$librs_path" librs-builder:latest cargo "$@"
    fi
}

set -x

IOS_TARGETS="aarch64-apple-ios x86_64-apple-ios aarch64-apple-ios-sim"
ALL_TARGETS="aarch64-unknown-linux-musl x86_64-unknown-linux-musl i686-unknown-linux-musl aarch64-apple-darwin x86_64-apple-darwin aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android $IOS_TARGETS"

# Allow callers to override the targets we'll build
if [ -z "$TARGETS" ]; then
    TARGETS="$ALL_TARGETS"
fi

included_ios_targets=0
for target in $IOS_TARGETS; do
    if [[ "$TARGETS" == *"$target"* ]]; then
        included_ios_targets=$((included_ios_targets + 1))
    fi
done

if [[ $included_ios_targets -ne 0 ]] && [[ $included_ios_targets -ne 3 ]]; then
    echo "You must either include all iOS targets or none"
    echo "iOS targets are: $IOS_TARGETS"
    exit 1
fi

if [ -z "$USE_HOST_CARGO" ]; then
    # Build a docker image with the necessary rust toolchains we'll use for all subsequent builds
    docker build --file libs.Dockerfile --tag librs-builder:latest --build-arg "TARGETS=${TARGETS}" .
fi

# Pre generate the proof/verifier data for faster runtime verification
mkdir -p bindings/src/bin
_cargo run --release -p generate

# Build the verifier for each supported target (android, macOS, linux, iOS)
mkdir -p libs
for target in $TARGETS; do
    _cargo build --release -p bindings --target $target --features precomputed_verifier_data
    cp target/$target/release/libbindings.a libs/$target-librs.a
done

if [[ $included_ios_targets -eq 0 ]]; then
    echo "Skipping building .xcframework for iOS targets since no iOS target was built"
    exit 0
fi

if [[ $(uname) != "Darwin" ]]; then
    echo "Skipping building .xcframework iOS/macOS targets; macOS is required to build them."
    exit 0
fi

# Check we have the necessary tools

if ! which lipo > /dev/null; then
    echo "Can't find required build tool lipo"
    exit 1
fi

if ! which xcodebuild > /dev/null; then
    echo "Can't find required build tool xcodebuild"
    exit 1
fi

# Xcode has no way to include .a for multiple targets natively. We need to construct a .xcframework/

# Join iOS simulator targets into a fat library, xcframeworks expects them together
lipo -create libs/x86_64-apple-ios-librs.a libs/aarch64-apple-ios-sim-librs.a -output libs/multi-apple-ios-sim-librs.a

# Remove the old .xcframework folder cause otherwise the creation below will error out (and yes, it's a folder, not a file)
if [[ -e libs/librs.xcframework ]]; then
    rm -r libs/librs.xcframework
fi

# Package iOS simulator and iOS device targets into a single framework for Xcodes happyness
xcodebuild -create-xcframework -library libs/aarch64-apple-ios-librs.a -headers librs.h -library libs/multi-apple-ios-sim-librs.a -headers librs.h -output libs/librs.xcframework

# We don't need the individual libraries anymore
rm libs/x86_64-apple-ios-librs.a libs/aarch64-apple-ios-sim-librs.a libs/aarch64-apple-ios-librs.a libs/multi-apple-ios-sim-librs.a
