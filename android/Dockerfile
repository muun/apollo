# VERY IMPORTANT!!!
# This file is used by build-release.yml (https://github.com/muun/apollo/blob/eedb679b9a5b7af2c617cc794c8dcf53eb1b4321/.github/workflows/build-release.yml).
# Any change made here will affect how the APK is built in the OSS repository (github action)
# and will impact the reproducible build verification.

FROM --platform=linux/amd64 eclipse-temurin:17.0.20.1_1-jdk-noble@sha256:78dde986267ae7cac62fe7ef624c2a5cee2036b6822933d39fd3870618b91526 AS muun_android_builder

ENV NDK_VERSION=22.0.7026061
ENV ANDROID_PLATFORM_VERSION=28
ENV ANDROID_BUILD_TOOLS_VERSION=28.0.3
ENV GO_VERSION=1.24.7
# From https://go.dev/dl/, must change with GO_VERSION
ENV GO_SHA256=da18191ddb7db8a9339816f3e2b54bdded8047cdc2a5d67059478f8d1595c43f

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        unzip \
        wget \
        ca-certificates \
        curl \
        git \
        zip \
        p11-kit \
    && rm -rf /var/lib/apt/lists/*

# The JDK ships its own CA bundle, which still trusts emSign ECC Root CA - G3, one of the roots
# behind the leaked eMudhra intermediates. Build the JDK cacerts from the system store on every CA
# update, since the ca-certificates package distrusts those roots, and fail the build if either
# store still has them.
RUN printf '#!/bin/sh\nexec trust extract --overwrite --format=java-cacerts --filter=ca-anchors --purpose=server-auth "%s/lib/security/cacerts"\n' "$JAVA_HOME" \
        > /etc/ca-certificates/update.d/java-cacerts \
    && chmod +x /etc/ca-certificates/update.d/java-cacerts \
    && update-ca-certificates --fresh \
    && certs=$(openssl crl2pkcs7 -nocrl -certfile /etc/ssl/certs/ca-certificates.crt | openssl pkcs7 -print_certs -noout) \
    && [ -n "$certs" ] \
    && ! printf '%s\n' "$certs" | grep -q -e 'emSign ECC Root CA - G3' -e 'AAA Certificate Services' \
    && jdk_certs=$(keytool -list -v -cacerts -storepass changeit) \
    && [ -n "$jdk_certs" ] \
    && ! printf '%s\n' "$jdk_certs" | grep -q -e 'emSign ECC Root CA - G3' -e 'AAA Certificate Services'

# install golang
RUN curl -L "https://golang.org/dl/go${GO_VERSION}.linux-amd64.tar.gz" \
	-o go${GO_VERSION}.linux-amd64.tar.gz && \
	echo "${GO_SHA256}  go${GO_VERSION}.linux-amd64.tar.gz" | sha256sum --check --strict - && \
	tar -xvf go${GO_VERSION}.linux-amd64.tar.gz -C /usr/local/  && \
	rm -rf go${GO_VERSION}.linux-amd64.tar.gz

ENV GOPATH=/go
ENV PATH=$GOPATH/bin:/usr/local/go/bin:$PATH

# install android sdk

ENV ANDROID_HOME=/opt/android-sdk-linux
ENV ANDROID_SDK_ROOT=/opt/android-sdk-linux

RUN cd /opt \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip -O android-sdk-tools.zip \
    && echo "89f308315e041c93a37a79e0627c47f21d5c5edbe5e80ea8dc0aac8a649e0e92  android-sdk-tools.zip" | sha256sum --check --strict - \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && unzip -q android-sdk-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && rm android-sdk-tools.zip

ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/tools/bin:${ANDROID_HOME}/platform-tools

# Accept licenses before installing components
RUN yes | sdkmanager --licenses

# Platform tools
RUN sdkmanager "platform-tools"

RUN yes | sdkmanager --update --channel=3

# Please keep these in descending order!
# The `yes` is for accepting all non-standard tool licenses.
RUN yes | sdkmanager \
    "platforms;android-${ANDROID_PLATFORM_VERSION}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

RUN sdkmanager --install "ndk;${NDK_VERSION}"

ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/${NDK_VERSION}

FROM --platform=linux/amd64 rust:1.84@sha256:738ae99a3d75623f41e6882566b4ef37e38a9840244a47efd4a0ca22e9628b88 AS rust_builder

ARG RUSTUP_TOOLCHAIN=nightly-2024-12-16
ARG TARGETS="aarch64-unknown-linux-musl x86_64-unknown-linux-musl aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android"

# Debian's ca-certificates still trusts the roots behind the leaked eMudhra intermediates, so
# distrust them before rustup and cargo download anything, and fail the build if they remain.
RUN sed -i \
        -e 's|^mozilla/emSign_ECC_Root_CA_-_G3\.crt$|!&|' \
        -e 's|^mozilla/Comodo_AAA_Services_root\.crt$|!&|' \
        /etc/ca-certificates.conf \
    && update-ca-certificates --fresh \
    && certs=$(openssl crl2pkcs7 -nocrl -certfile /etc/ssl/certs/ca-certificates.crt | openssl pkcs7 -print_certs -noout) \
    && [ -n "$certs" ] \
    && ! printf '%s\n' "$certs" | grep -q -e 'emSign ECC Root CA - G3' -e 'AAA Certificate Services'

# Newer rustup versions download with their own CA roots, so rustup must not update itself, and
# the curl backend keeps it on the system store if the base image ever brings one of them.
ENV RUSTUP_USE_CURL=1

RUN rustup set auto-self-update disable \
    && rustup toolchain install ${RUSTUP_TOOLCHAIN} \
    && rustup override set ${RUSTUP_TOOLCHAIN} \
    && rustup target add --toolchain ${RUSTUP_TOOLCHAIN} $TARGETS \
    && rustup component add clippy \
    && rustup component add rustfmt

FROM rust_builder AS librs_build

WORKDIR /src
COPY . /src

RUN TARGETS="$TARGETS" USE_HOST_CARGO=true libwallet/librs/makelibs.sh

FROM muun_android_builder AS build

WORKDIR /src
COPY . /src
COPY --from=librs_build /src/libwallet/librs/libs /src/libwallet/librs/libs

# TODO: Gradle checks no checksum for the Maven dependencies it downloads, so they rely on TLS
# alone. Add Gradle dependency verification (gradle/verification-metadata.xml).
RUN tools/bootstrap-gomobile.sh \
    && (cd libwallet; go clean) \
    && ./gradlew :android:apolloui:clean \
    && ./gradlew :android:libwallet:build :android:apolloui:assembleProdRelease

FROM scratch

COPY --from=build /src/android/apolloui/build/outputs/apk/prod/release/apolloui-prod-arm64-v8a-release-unsigned.apk apolloui-prod-arm64-v8a-release-unsigned.apk
COPY --from=build /src/android/apolloui/build/outputs/apk/prod/release/apolloui-prod-armeabi-v7a-release-unsigned.apk apolloui-prod-armeabi-v7a-release-unsigned.apk
COPY --from=build /src/android/apolloui/build/outputs/apk/prod/release/apolloui-prod-x86-release-unsigned.apk apolloui-prod-x86-release-unsigned.apk
COPY --from=build /src/android/apolloui/build/outputs/apk/prod/release/apolloui-prod-x86_64-release-unsigned.apk apolloui-prod-x86_64-release-unsigned.apk
COPY --from=build /src/android/apolloui/build/outputs/mapping/prodRelease/mapping.txt mapping.txt
