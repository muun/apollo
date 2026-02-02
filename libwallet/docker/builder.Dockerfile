FROM golang:1.24-bullseye

ENV STATICCHECK_VERSION=2025.1.1

# install staticcheck (linter for go projects)
RUN go install "honnef.co/go/tools/cmd/staticcheck@${STATICCHECK_VERSION}"