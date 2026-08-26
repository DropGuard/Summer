#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?Usage: $0 <release-version> [next-snapshot-version]}"
NEXT_VERSION="${2:-}"

if [[ -z "${NEXT_VERSION}" ]]; then
    NEXT_VERSION="$(echo "${VERSION}" | awk -F. '{$NF = $NF + 1; print $0}' OFS=.)-SNAPSHOT"
fi

echo "==> Releasing v${VERSION} (Next: ${NEXT_VERSION})"

mvn -f summer-parent/pom.xml versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false -T 1
mvn -f samples/pom.xml versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false -T 1
mvn -f summer-benchmark/pom.xml versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false -DprocessParent=true -T 1

mvn spotless:check

git commit -am "chore: bump version to ${VERSION}"
git tag "v${VERSION}"

mvn -f summer-parent/pom.xml versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false -T 1
mvn -f samples/pom.xml versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false -T 1
mvn -f summer-benchmark/pom.xml versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false -DprocessParent=true -T 1
git commit -am "chore: bump development version to ${NEXT_VERSION}"

git push origin main
git push origin "v${VERSION}"
