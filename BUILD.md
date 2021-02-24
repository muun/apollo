# Building locally

## Requirements

* [OpenJDK](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot) >= 8
* [Golang](https://golang.org/dl/) >= 1.16
* [Android NDK](https://developer.android.com/ndk/downloads) >= 21

## Instructions

Ensure the env var `ANDROID_NDK_HOME` points to your NDK install directory.

```shell
tools/bootstrap-gomobile.sh
tools/libwallet-android.sh
./gradlew :android:apollo:assembleProdRelease
```

# Reproducible build

## Requirements

* [Docker](https://www.docker.com/)

## Instructions

```shell
docker build -f android/Dockerfile -t muun_android:latest .
docker run --rm -ti -v "$PWD:/src/android/apolloui/build/outputs/" muun_android:latest
```

# (Advanced) Verifying an existing APK

## Requirements

* [Docker](https://www.docker.com/)
* (Optional) [ADB](https://developer.android.com/studio/releases/platform-tools)

## Instructions

### (Optional) Obtaining the APK from your phone

```shell
adb pull $(adb shell pm path io.muun.apollo | grep "/base.apk" | sed 's/^package://') apollo-play.apk
```

### Verifying

Checkout the commit that corresponds to the version of the app you want to verify.

```shell
tools/verify-apollo.sh <path-to-verify.apk>
```

