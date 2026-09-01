#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
POSITIONAL_ARGS=()

for arg in "$@"; do
    case "${arg}" in
        --dry-run|-n)
            DRY_RUN=true
            ;;
        *)
            POSITIONAL_ARGS+=("${arg}")
            ;;
    esac
done

if [[ ${#POSITIONAL_ARGS[@]} -lt 1 ]]; then
    echo "Usage: $0 [--dry-run] <release-version> [next-snapshot-version]" >&2
    echo "Example: $0 --dry-run 0.3.3" >&2
    exit 1
fi

VERSION="${POSITIONAL_ARGS[0]}"
NEXT_VERSION="${POSITIONAL_ARGS[1]:-}"

if [[ -z "${NEXT_VERSION}" ]]; then
    NEXT_VERSION="$(echo "${VERSION}" | awk -F. '{$NF = $NF + 1; print $0}' OFS=.)-SNAPSHOT"
fi

# Detect current version from summer-parent/pom.xml
CURRENT_VERSION=$(python3 -c "
import xml.etree.ElementTree as ET
try:
    tree = ET.parse('summer-parent/pom.xml')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    v = tree.find('m:version', ns)
    print(v.text if v is not None else '')
except Exception:
    print('')
")

if [[ -z "${CURRENT_VERSION}" ]]; then
    echo "ERROR: Could not detect current version from summer-parent/pom.xml" >&2
    exit 1
fi

set_pom_versions() {
    local target_ver="$1"
    local dry_run="$2"
    python3 -c "
import os

target = '${target_ver}'
current = '${CURRENT_VERSION}'
dry_run = ('${dry_run}' == 'true')
count = 0
affected = []

for root, dirs, files in os.walk('.'):
    if any(p in root for p in ['target', '.git', '.idea', 'node_modules']):
        continue
    for file in files:
        if file == 'pom.xml' or file == 'docker-compose.yml':
            p = os.path.join(root, file)
            with open(p, 'r', encoding='utf-8') as f:
                content = f.read()
            target_tag = f'<version>{target}</version>'
            current_tag = f'<version>{current}</version>'
            target_prop = f'<summer.framework.version>{target}</summer.framework.version>'
            current_prop = f'<summer.framework.version>{current}</summer.framework.version>'
            target_jar = f'benchmark-summer-{target}.jar'
            current_jar = f'benchmark-summer-{current}.jar'
            target_jsonb_jar = f'benchmark-summer-jsonb-{target}.jar'
            current_jsonb_jar = f'benchmark-summer-jsonb-{current}.jar'
            if current_tag in content or current_prop in content or current_jar in content:
                count += 1
                affected.append(p)
                if not dry_run:
                    new_content = (content
                        .replace(current_tag, target_tag)
                        .replace(current_prop, target_prop)
                        .replace(current_jar, target_jar)
                        .replace(current_jsonb_jar, target_jsonb_jar))
                    with open(p, 'w', encoding='utf-8') as f:
                        f.write(new_content)

action = '[DRY-RUN] Would update' if dry_run else 'Updated'
print(f'==> {action} {count} files ({current} -> {target}):')
for p in sorted(affected):
    print(f'    - {p}')
"
    if [[ "${dry_run}" != "true" ]]; then
        CURRENT_VERSION="${target_ver}"
    fi
}

echo "================================================================"
if [[ "${DRY_RUN}" == "true" ]]; then
    echo "🚀 [DRY RUN MODE] Simulating release v${VERSION}"
else
    echo "🚀 [LIVE MODE] Executing release v${VERSION}"
fi
echo "   Current Version : ${CURRENT_VERSION}"
echo "   Release Target  : v${VERSION}"
echo "   Next Dev Target : ${NEXT_VERSION}"
echo "================================================================"

if [[ "${DRY_RUN}" == "true" ]]; then
    echo ""
    echo "--- Step 1: Scan POM files for release bump ---"
    set_pom_versions "${VERSION}" "true"

    echo ""
    echo "--- Step 2: Code format check (read-only) ---"
    mvn spotless:check

    echo ""
    echo "--- Step 3: Planned Git operations (skipped in dry-run) ---"
    echo "    1. git commit -am \"chore: bump version to ${VERSION}\""
    echo "    2. git tag \"v${VERSION}\""
    echo "    3. Bump all POMs: ${VERSION} -> ${NEXT_VERSION}"
    echo "    4. git commit -am \"chore: bump development version to ${NEXT_VERSION}\""
    echo "    5. git push origin main"
    echo "    6. git push origin \"v${VERSION}\""
    echo ""
    echo "✅ [DRY RUN SUCCESS] All checks passed! No files or git state were modified."
    exit 0
fi

# 1. Bump to release version
echo ""
echo "--- Step 1: Updating POMs to release version ${VERSION} ---"
set_pom_versions "${VERSION}" "false"
mvn spotless:check

git commit -am "chore: bump version to ${VERSION}"
git tag "v${VERSION}"

# 2. Bump to next snapshot version
echo ""
echo "--- Step 2: Updating POMs to next dev version ${NEXT_VERSION} ---"
set_pom_versions "${NEXT_VERSION}" "false"
mvn spotless:check

git commit -am "chore: bump development version to ${NEXT_VERSION}"

# 3. Push main and tag
echo ""
echo "--- Step 3: Pushing commits and tag to origin ---"
git push origin main
git push origin "v${VERSION}"

echo ""
echo "🎉 Successfully released v${VERSION} and bumped main to ${NEXT_VERSION}!"
