#!/bin/bash

BASE_VERSION="jdk-8.0.0.jar"
NEW_VERSION="jdk-8.0.0-deterministic.jar"

pkgdiff -check-byte-code -extra-info pkgdiff_extra \
    "$BASE_VERSION" "$NEW_VERSION"

${SHELL} tools/find-exclusions.sh

sed -n '1,/\/\* DATASET \*\//p' < ./tools/report-template.html > report.html
${SHELL} tools/generate-report.sh >> report.html
sed -n '/\/\* DATASET \*\//,$p' < ./tools/report-template.html >> report.html
