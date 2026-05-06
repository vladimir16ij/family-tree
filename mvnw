#!/bin/sh
# Apache Maven Wrapper
# Minimal POSIX launcher that downloads the wrapper jar if missing.

set -eu

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPS_FILE="$WRAPPER_DIR/maven-wrapper.properties"
JAR_FILE="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS_FILE" ]; then
  echo "Missing $PROPS_FILE" >&2
  exit 1
fi

wrapperUrl="$(grep -E '^wrapperUrl=' "$PROPS_FILE" | cut -d= -f2-)"
if [ -z "${wrapperUrl:-}" ]; then
  echo "wrapperUrl is missing in $PROPS_FILE" >&2
  exit 1
fi

mkdir -p "$WRAPPER_DIR"

if [ ! -f "$JAR_FILE" ]; then
  echo "Downloading Maven Wrapper jar..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$wrapperUrl" -o "$JAR_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$wrapperUrl" -O "$JAR_FILE"
  else
    echo "Neither curl nor wget found. Install one to download Maven Wrapper." >&2
    exit 1
  fi
fi

JAVA_CMD="${JAVA_HOME:-}/bin/java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_CMD" ]; then
  : # use JAVA_HOME
else
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -classpath "$JAR_FILE" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

