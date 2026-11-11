#!/bin/sh
set -eu
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export MYPODCASTS_STORE_FILE="${MYPODCASTS_STORE_FILE:-$HOME/keys/my-podcasts-release.p12}"
export MYPODCASTS_KEY_ALIAS="${MYPODCASTS_KEY_ALIAS:-mypodcasts}"
if [ -z "${MYPODCASTS_STORE_PASSWORD:-}" ]; then
    MYPODCASTS_STORE_PASSWORD=$(security find-generic-password -a "$USER" -s com.homeo.mypodcasts.release -w)
    export MYPODCASTS_STORE_PASSWORD
fi
export MYPODCASTS_KEY_PASSWORD="${MYPODCASTS_KEY_PASSWORD:-$MYPODCASTS_STORE_PASSWORD}"
test -f "$MYPODCASTS_STORE_FILE"
test -n "$MYPODCASTS_STORE_PASSWORD"
exec ./gradlew :app:assembleFreeRelease "$@"
