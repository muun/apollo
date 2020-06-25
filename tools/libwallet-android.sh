#!/bin/bash

repo_root=$(git rev-parse --show-toplevel)
libwallet="$1"
if [[ ! -s "$1" ]]; then
    libwallet="$repo_root/android/apollo/libs/libwallet.aar"
fi

(cd "$repo_root/libwallet"; GO111MODULE=off go get)

# Hack for bad btcd version
(cd $GOPATH/src/github.com/btcsuite/btcd && git checkout v0.20.1-beta)

GO111MODULE=off gomobile bind -target=android -o "$libwallet" github.com/muun/muun/libwallet

st=$?
echo "rebuilt gomobile with status $? to $libwallet"
exit $st

