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

tmp=$(mkdir -d)

# Prepare paths to extract APKs
mkdir -p "$tmp/to_verify" "$tmp/baseline"

echo "Building the APK from source. This might take a while (10-20 minutes)..."

docker build -f android/Dockerfile -t muun_android:latest .
docker run --rm -ti -v "$PWD:/src/android/apolloui/build/outputs/" muun_android:latest

unzip -q -d "$tmp/to_verify" "$apk_to_verify"
unzip -q -d "$tmp/baseline" "apk/prod/release/apolloui-prod-release-unsigned.apk"

# TODO: verify the signature

# Remove the signature since OSS users won't have Muuns private signing key
rm -r "$tmp"/{to_verify,baseline}/{META-INF,resources.arsc}

diff -r "$tmp/to_verify" "$tmp/baseline" && echo "Verification success!" || echo "Verification failed :("


