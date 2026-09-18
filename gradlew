#!/bin/sh

#
# Gradle Wrapper launcher
#

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: Java tidak ditemukan." >&2
    exit 1
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar tidak ditemukan." >&2
    exit 1
fi

exec "$JAVACMD" \
    ${JAVA_OPTS:-} \
    ${GRADLE_OPTS:-} \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
