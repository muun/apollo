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

# Prepare paths to extract APKs
mkdir -p "$tmp/to_verify" "$tmp/baseline"

echo "Building the APK from source. This might take a while (10-20 minutes)..."

mkdir -p apk
docker build -f android/Dockerfile -o apk .

unzip -q -d "$tmp/to_verify" "$apk_to_verify"
unzip -q -d "$tmp/baseline" "apk/apolloui-prod-release-unsigned.apk"

# TODO: verify the signature

# Remove the signature since OSS users won't have Muuns private signing key
rm -r "$tmp"/{to_verify,baseline}/{META-INF,resources.arsc}

diff -r "$tmp/to_verify" "$tmp/baseline" && echo "Verification success!" || echo "Verification failed :("


