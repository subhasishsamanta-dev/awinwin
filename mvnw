#!/usr/bin/env bash
# Simple Maven wrapper for Unix-like shells: downloads Maven locally if not installed and runs it
if command -v mvn >/dev/null 2>&1; then
  mvn "$@"
  exit $?
fi
MAVEN_VERSION=3.9.6
WRAPPER_DIR="$(dirname "$0")/.mvn/wrapper"
MAVEN_ZIP="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_DIR="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
mkdir -p "$WRAPPER_DIR"
if [ ! -d "$MAVEN_DIR/bin" ]; then
  if [ ! -f "$MAVEN_ZIP" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    curl -L -o "$MAVEN_ZIP" "https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip"
  fi
  echo "Extracting Maven..."
  unzip -oq "$MAVEN_ZIP" -d "$WRAPPER_DIR"
fi
"$MAVEN_DIR/bin/mvn" "$@"
