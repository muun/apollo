# Build locally

### Requirements

* [OpenJDK](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot) >= 8
* [Golang](https://golang.org/dl/) >= 1.16
* [Android NDK](https://developer.android.com/ndk/downloads) >= 21
* [Docker](https://docs.docker.com/engine/install/) >= 28

### Instructions

1. Ensure the environment variable `ANDROID_NDK_HOME` points to your NDK installation directory.
2. Make sure docker is up and running
3. Run:
    ```shell
    libwallet/librs/makelibs.sh
    tools/bootstrap-gomobile.sh
    tools/libwallet-android.sh
    ./gradlew :android:apolloui:assembleProdRelease
    ```


# Build reproducibly

### Requirements

* [Docker](https://www.docker.com/) >= 18.09

### Instructions

1. Ensure Docker has at least 5 GB of RAM and run:
    ```shell
    mkdir -p apk
    DOCKER_BUILDKIT=1 docker build -f android/Dockerfile -o apk .
    ```


# Verify an existing APK

### Requirements

* [Docker](https://www.docker.com/) >= 18.09
* [ADB](https://developer.android.com/studio/releases/platform-tools)

### Instructions

1. Obtain the APK from your phone by connecting it to the computer and running:
    ```shell
    adb pull $(adb shell pm path io.muun.apollo | grep "/base.apk" | sed 's/^package://') apollo-play.apk
    ```
2. Checkout the commit that corresponds to the version of the app you want to verify.
3. Ensure Docker has at least 5 GB of RAM and run:
    ```shell
    tools/verify-apollo.sh <path-to-verify.apk>
    ```

