#!/bin/bash

# Prefetch some dependencies so that we can use a specific version that actually builds
go get github.com/btcsuite/btcd github.com/lightninglabs/gozmq github.com/lightninglabs/neutrino
(cd $GOPATH/src/github.com/btcsuite/btcd && git checkout v0.20.1-beta > /dev/null)
(cd $GOPATH/src/github.com/lightninglabs/gozmq && git checkout d20a764486bf > /dev/null)
(cd $GOPATH/src/github.com/lightninglabs/neutrino && git checkout v0.11.0 > /dev/null)

go get golang.org/x/mobile/cmd/gomobile golang.org/x/tools/go/packages 
PATH="$PATH:$GOPATH/bin" gomobile init
mkdir -p $GOPATH/src/github.com/muun/muun/
ln -s $PWD/libwallet $GOPATH/src/github.com/muun/muun/libwallet
(cd libwallet && GO111MODULE=off go get)
