FROM golang:1.22.6-bullseye

ENV STATICCHECK_VERSION=2024.1.1

# install staticcheck (linter for go projects)
RUN go install "honnef.co/go/tools/cmd/staticcheck@${STATICCHECK_VERSION}"