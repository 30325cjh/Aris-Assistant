#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
WRAPPER_BASE64="$WRAPPER_JAR.base64"
EXPECTED_SHA=$(tr -d '[:space:]' < "$WRAPPER_JAR.sha256")

if [ ! -f "$WRAPPER_JAR" ]; then
    TEMP_JAR="$WRAPPER_JAR.tmp"
    if base64 --decode "$WRAPPER_BASE64" > "$TEMP_JAR" 2>/dev/null; then
        :
    elif base64 -D "$WRAPPER_BASE64" > "$TEMP_JAR" 2>/dev/null; then
        :
    else
        rm -f "$TEMP_JAR"
        echo "Unable to decode the Gradle wrapper JAR. Install the base64 utility and retry." >&2
        exit 1
    fi
    mv "$TEMP_JAR" "$WRAPPER_JAR"
fi

if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA=$(sha256sum "$WRAPPER_JAR" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA=$(shasum -a 256 "$WRAPPER_JAR" | awk '{print $1}')
else
    echo "A SHA-256 utility (sha256sum or shasum) is required to verify Gradle." >&2
    exit 1
fi

if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
    rm -f "$WRAPPER_JAR"
    echo "Gradle wrapper JAR checksum mismatch; refusing to execute it." >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

exec "$JAVA" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
