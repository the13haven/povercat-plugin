#!/usr/bin/env sh

# Copyright 2026
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
# OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <stable-gradle-version>" >&2
    exit 2
fi

version=$1
if ! printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "Invalid stable Gradle version: $version" >&2
    exit 2
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required to download official Gradle checksums." >&2
    exit 1
fi

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
project_dir=$(CDPATH= cd "$script_dir/.." && pwd)
cd "$project_dir"

distribution_checksum_url="https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256"
wrapper_checksum_url="https://services.gradle.org/distributions/gradle-$version-wrapper.jar.sha256"

download_checksum() {
    checksum=$(curl --fail --silent --show-error --location --retry 3 "$1" | tr -d '[:space:]')
    if ! printf '%s\n' "$checksum" | grep -Eq '^[0-9a-f]{64}$'; then
        echo "Invalid SHA-256 checksum received from $1" >&2
        exit 1
    fi
    printf '%s\n' "$checksum"
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{ print $1 }'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{ print $1 }'
    else
        echo "sha256sum or shasum is required to verify the Wrapper JAR." >&2
        exit 1
    fi
}

distribution_checksum=$(download_checksum "$distribution_checksum_url")
wrapper_checksum=$(download_checksum "$wrapper_checksum_url")

echo "Updating Gradle Wrapper to $version..."

# The first invocation updates gradle-wrapper.properties. The second invocation
# runs with the target Gradle distribution and refreshes the scripts and Wrapper JAR.
./gradlew wrapper \
    --gradle-version "$version" \
    --distribution-type bin \
    --gradle-distribution-sha256-sum "$distribution_checksum"
./gradlew wrapper \
    --gradle-version "$version" \
    --distribution-type bin \
    --gradle-distribution-sha256-sum "$distribution_checksum"

properties_file="gradle/wrapper/gradle-wrapper.properties"
expected_url="distributionUrl=https\\://services.gradle.org/distributions/gradle-$version-bin.zip"

if ! grep -Fqx "$expected_url" "$properties_file"; then
    echo "Unexpected distributionUrl after Wrapper update." >&2
    exit 1
fi

if ! grep -Fqx "distributionSha256Sum=$distribution_checksum" "$properties_file"; then
    echo "Unexpected distributionSha256Sum after Wrapper update." >&2
    exit 1
fi

actual_wrapper_checksum=$(sha256_file "gradle/wrapper/gradle-wrapper.jar")
if [ "$actual_wrapper_checksum" != "$wrapper_checksum" ]; then
    echo "Gradle Wrapper JAR checksum mismatch." >&2
    echo "Expected: $wrapper_checksum" >&2
    echo "Actual:   $actual_wrapper_checksum" >&2
    exit 1
fi

echo "Gradle Wrapper $version updated and verified successfully."
