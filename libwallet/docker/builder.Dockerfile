FROM golang:1.24-bullseye

ENV GOLANGCI_LINT_VERSION=v2.8.0

RUN go install "github.com/golangci/golangci-lint/v2/cmd/golangci-lint@${GOLANGCI_LINT_VERSION}" \
    && cp /go/bin/golangci-lint /usr/local/bin/golangci-lint \
    && go install golang.org/x/tools/cmd/goimports@v0.40.0 \
    && cp /go/bin/goimports /usr/local/bin/goimports