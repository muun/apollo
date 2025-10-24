# VERY IMPORTANT!!!
# This file is used by build-release.yml (https://github.com/muun/apollo/blob/eedb679b9a5b7af2c617cc794c8dcf53eb1b4321/.github/workflows/build-release.yml).
# Any change made here will affect how the APK is built in the OSS repository (github action)
# and will impact the reproducible build verification.

FROM --platform=linux/amd64 openjdk:17-jdk-slim@sha256:aaa3b3cb27e3e520b8f116863d0580c438ed55ecfa0bc126b41f68c3f62f9774 AS muun_android_builder

ENV NDK_VERSION 22.0.7026061
ENV ANDROID_PLATFORM_VERSION 28
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV GO_VERSION 1.24.7

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        unzip \
        wget \
        ca-certificates \
        curl \
        git \
        zip \
    && rm -rf /var/lib/apt/lists/*


# install golang
RUN curl -L "https://golang.org/dl/go${GO_VERSION}.linux-amd64.tar.gz" \
	-o go${GO_VERSION}.linux-amd64.tar.gz && \
	tar -xvf go${GO_VERSION}.linux-amd64.tar.gz -C /usr/local/  && \
	rm -rf go${GO_VERSION}.linux-amd64.tar.gz

ENV GOPATH /go
ENV PATH $GOPATH/bin:/usr/local/go/bin:$PATH

# install android sdk

ENV ANDROID_HOME /opt/android-sdk-linux
ENV ANDROID_SDK_ROOT /opt/android-sdk-linux

RUN cd /opt \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip -O android-sdk-tools.zip \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && unzip -q android-sdk-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && rm android-sdk-tools.zip

ENV PATH ${PATH}:${ANDROID_HOME}/cmdline-tools/tools/bin:${ANDROID_HOME}/platform-tools

# Accept licenses before installing components
RUN yes | sdkmanager --licenses

# Platform tools
RUN sdkmanager "tools" "platform-tools"

RUN yes | sdkmanager --update --channel=3

# Please keep these in descending order!
# The `yes` is for accepting all non-standard tool licenses.
RUN yes | sdkmanager \
    "platforms;android-${ANDROID_PLATFORM_VERSION}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

RUN sdkmanager --install "ndk;${NDK_VERSION}"

ENV ANDROID_NDK_HOME ${ANDROID_HOME}/ndk/${NDK_VERSION}

FROM rust:1.84 AS rust_builder

ARG RUSTUP_TOOLCHAIN=nightly-2024-12-16
ARG TARGETS="aarch64-unknown-linux-musl x86_64-unknown-linux-musl aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android"

RUN rustup toolchain install ${RUSTUP_TOOLCHAIN} \
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
