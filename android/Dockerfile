FROM openjdk:14.0.2-jdk-buster@sha256:12fa83956c7ba2423622e293eced64b475e6e3bb151c06c54812d5d8c39f4bc0 AS muun_android_builder

ENV NDK_VERSION 22.0.7026061
ENV ANDROID_PLATFORM_VERSION 28
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV GO_VERSION 1.15.8

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

FROM muun_android_builder

WORKDIR /src
COPY . /src
CMD tools/bootstrap-gomobile.sh \
    && (cd libwallet; go clean) \
    && tools/libwallet-android.sh \
    && ./gradlew :android:apollo:clean :android:apolloui:clean \
    && ./gradlew :android:apolloui:assembleProdRelease