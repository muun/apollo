#!/bin/bash

go get golang.org/x/mobile/cmd/gomobile
PATH="$PATH:$GOPATH/bin" gomobile init
