#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Prefer the Rosetta x64 Temurin JDK when present: arm64 HotSpot SIGBUS-crashes
# with SIP disabled on this Mac (OpenJDK JDK-8326663). Falls back to JAVA_HOME,
# then any installed JDK 21.
PUCK_ROSETTA_JDK="$HOME/Library/Java/JavaVirtualMachines/temurin-21-x64/Contents/Home"
if [ -x "${PUCK_ROSETTA_JDK}/bin/java" ]; then
    PUCK_JAVA_HOME="${PUCK_ROSETTA_JDK}"
else
    PUCK_JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
fi
if [ -d /opt/homebrew/opt/expat/lib ]; then
    export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
fi
# Invoke the same pinned Gradle wrapper directly: macOS strips DYLD variables
# when traversing the system shell used by gradlew, breaking current Brew Python.
exec "$PUCK_JAVA_HOME/bin/java" -Xmx64m -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
