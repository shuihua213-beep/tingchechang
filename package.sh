#!/bin/bash
set -e

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
BUILD_DIR="$ROOT_DIR/cf-framework-parent"
JAR_DIR="$ROOT_DIR/jar"

cd "$BUILD_DIR"
mvn clean install -DskipTests

rm -rf "$JAR_DIR"
mkdir -p "$JAR_DIR"
find "$BUILD_DIR" -path "*/target/*.jar" ! -name "*original*" ! -name "*sources.jar" ! -name "*javadoc.jar" -exec cp {} "$JAR_DIR"/ \;
