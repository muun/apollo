#!/bin/bash

set -e

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <path-to-verify.apk>"
    exit 1
fi

apk_to_verify="$1"

if [ ! -f "$apk_to_verify" ]; then
    echo "$apk_to_verify is not an existing APK"
    exit 2
fi

# Go to repo root
cd $(git rev-parse --show-toplevel)

tmp=$(mktemp -d)

# Ensure temp directory is deleted when script exits
trap 'rm -rf "$tmp"' EXIT

# Prepare paths to extract APKs
mkdir -p "$tmp/to_verify" "$tmp/baseline"

echo "Building the APKs from source. This might take a while (10-20 minutes)..."
mkdir -p apk
DOCKER_BUILDKIT=1 docker build -f android/Dockerfile -o apk .

# Clean to_verify directory before unzipping
rm -rf "$tmp/to_verify"

# Unzip the APK to verify
mkdir -p "$tmp/to_verify"
unzip -q -o "$apk_to_verify" -d "$tmp/to_verify"

# TODO: verify the signature

# Remove the signature since OSS users won't have Muuns private signing key
rm -r "$tmp"/to_verify/{META-INF,resources.arsc}

# Compare /lib first
lib_dirs_in_verify=($(find "$tmp/to_verify/lib" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true))

echo "Found lib directory in APK to verify: ${lib_dirs_in_verify[*]}"

if [ ${#lib_dirs_in_verify[@]} -ne 1 ]; then
    echo "Unexpected lib directory structure in APK to verify."
    exit 3
fi

lib_dir_name=$(basename "${lib_dirs_in_verify[0]}")
echo "Using lib architecture: $lib_dir_name"

baseline_apk_dir=""

# Pick baseline based on lib architecture
case "$lib_dir_name" in
  arm64-v8a)
    baseline_apk_dir="apk/apolloui-prod-arm64-v8a-release-unsigned.apk"
    ;;
  armeabi-v7a)
    baseline_apk_dir="apk/apolloui-prod-armeabi-v7a-release-unsigned.apk"
    ;;
  x86)
    baseline_apk_dir="apk/apolloui-prod-x86-release-unsigned.apk"
    ;;
  x86_64)
    baseline_apk_dir="apk/apolloui-prod-x86_64-release-unsigned.apk"
    ;;
  *)
    echo "Unknown architecture: $lib_dir_name"
    exit 4
    ;;
esac

# Clean baseline directory before unzipping
rm -rf "$tmp/baseline"/*

unzip -q -o "$baseline_apk_dir" -d "$tmp/baseline"
rm -r "$tmp"/baseline/{META-INF,resources.arsc}

echo "Comparing files..."

diff_non_lib=$(diff -r "$tmp/to_verify" "$tmp/baseline" || true)

if [ -n "$diff_non_lib" ]; then
    echo "Verification failed :("
    exit 5
fi

echo "Verification success!"
