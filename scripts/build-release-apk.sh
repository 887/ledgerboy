#!/usr/bin/env bash
# build-release-apk.sh — build a release APK into release/ for distribution.
#
#   ./scripts/build-release-apk.sh                  # build APK into release/
#   ./scripts/build-release-apk.sh --gh-release     # build + upload to GitHub Releases
#   ./scripts/build-release-apk.sh --install        # build + adb install onto device
#   ./scripts/build-release-apk.sh --gh-release --install   # phone-vibing happy path
#
# Output: release/ledgerboy-<version>-<sha7>.apk + release/latest.apk symlink.
#
# Signing: debug keystore by default (fine for personal sideload). For a
# published release set LEDGER_RELEASE_KEYSTORE + LEDGER_RELEASE_KEY_ALIAS +
# LEDGER_RELEASE_KEY_PASSWORD and the Gradle release config picks them up.
#
# Local build is the primary path — zero CI minutes by default. The
# .github/workflows/release.yml fallback is tag-only and self-disabling
# (exits 0 if an APK is already attached to the tag's release).
set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
cd "${ROOT}"

# Android CLI's bundled JRE 21 lacks java.rmi — use a full system JDK.
# Toolchain: honour JAVA_HOME if set, else pick the newest installed full JDK
# (one with javac). Hardcoding a version broke when java-26 was replaced.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
    for _jdk in $(ls -d /usr/lib/jvm/java-*-openjdk 2>/dev/null | sort -V -r); do
        if [ -x "${_jdk}/bin/javac" ]; then export JAVA_HOME="${_jdk}"; break; fi
    done
fi
export JAVA_HOME
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

PUSH_TO_GH=false
INSTALL_TO_DEVICE=false
for arg in "$@"; do
    case "$arg" in
        --gh-release|--github)   PUSH_TO_GH=true ;;
        --install|--adb-install) INSTALL_TO_DEVICE=true ;;
        --help|-h) sed -n '1,/^set -euo/p' "$0" | sed 's/^# \?//;/^set -euo/d'; exit 0 ;;
        *) echo "unknown flag: $arg" >&2; exit 1 ;;
    esac
done

VERSION="$(awk -F'"' '/versionName *=/ {print $2; exit}' app/build.gradle.kts)"
[ -z "${VERSION}" ] && VERSION="1.0"
SHA7="$(git rev-parse --short=7 HEAD)"
SHA_FULL="$(git rev-parse HEAD)"
TAG="${VERSION}-${SHA7}"
REL_TAG="v${TAG}"

OUT_DIR="${ROOT}/release"
OUT_APK="${OUT_DIR}/ledgerboy-${TAG}.apk"
mkdir -p "${OUT_DIR}"

if [ -n "${LEDGER_RELEASE_KEYSTORE:-}" ]; then
    echo "[build-release-apk] release-signed build (keystore: ${LEDGER_RELEASE_KEYSTORE})"
    GRADLE_TASK="assembleRelease"
    BUILD_APK="app/build/outputs/apk/release/app-release.apk"
else
    echo "[build-release-apk] debug-signed build (set LEDGER_RELEASE_KEYSTORE for production signing)"
    GRADLE_TASK="assembleDebug"
    BUILD_APK="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "[build-release-apk] running ./gradlew :app:${GRADLE_TASK}..."
./gradlew ":app:${GRADLE_TASK}" --console=plain >/dev/null

[ -f "${BUILD_APK}" ] || { echo "[build-release-apk] expected APK at ${BUILD_APK} but it's missing" >&2; exit 1; }

cp "${BUILD_APK}" "${OUT_APK}"
ln -sf "$(basename "${OUT_APK}")" "${OUT_DIR}/latest.apk"
APK_SIZE="$(du -h "${OUT_APK}" | cut -f1)"
APK_SHA256="$(sha256sum "${OUT_APK}" | awk '{print $1}')"
echo "[build-release-apk] ${OUT_APK} (${APK_SIZE})"
echo "[build-release-apk] sha256: ${APK_SHA256}"

if "${INSTALL_TO_DEVICE}"; then
    echo "[build-release-apk] adb install -r ${OUT_APK}"
    adb install -r "${OUT_APK}"
fi

if "${PUSH_TO_GH}"; then
    command -v gh >/dev/null || { echo "[build-release-apk] gh CLI not found" >&2; exit 1; }
    PREV_TAG="$(git tag -l 'v*' --sort=-creatordate | grep -v "^${REL_TAG}\$" | head -n1 || true)"
    if [ -n "${PREV_TAG}" ]; then RANGE="${PREV_TAG}..HEAD"; HDR="Changes since \`${PREV_TAG}\`"; else RANGE="HEAD"; HDR="Recent changes"; fi
    COMMITS="$(git log "${RANGE}" --pretty=format:'- %s (%h)' | head -n 50)"
    [ -z "${COMMITS}" ] && COMMITS="- (no commits since previous tag)"
    NOTES="$(mktemp)"
    cat >"${NOTES}" <<EOF
Auto-built APK from commit \`${SHA7}\`.

## Install

Sideload via [Obtainium](https://github.com/ImranR98/Obtainium) or directly:

\`\`\`
adb install -r ledgerboy-${TAG}.apk
\`\`\`

## ${HDR}

${COMMITS}

## Verify build

| Field | Value |
| --- | --- |
| Commit | \`${SHA_FULL}\` |
| APK | \`ledgerboy-${TAG}.apk\` |
| APK SHA-256 | \`${APK_SHA256}\` |
| Build flavour | \`${GRADLE_TASK}\` |
EOF
    if gh release view "${REL_TAG}" >/dev/null 2>&1; then
        gh release upload "${REL_TAG}" "${OUT_APK}" --clobber
        gh release edit "${REL_TAG}" --notes-file "${NOTES}" >/dev/null
    else
        git rev-parse -q --verify "refs/tags/${REL_TAG}" >/dev/null || git tag -a "${REL_TAG}" -m "ledgerboy ${TAG}"
        # gh refuses a tag that exists only locally — push it first.
        git push origin "refs/tags/${REL_TAG}"
        gh release create "${REL_TAG}" "${OUT_APK}" --title "ledgerboy ${VERSION} (${SHA7})" --notes-file "${NOTES}"
    fi
    rm -f "${NOTES}"
    git push origin "refs/tags/${REL_TAG}" 2>/dev/null || echo "[build-release-apk] tag push skipped (no origin / already pushed)"
    gh release view "${REL_TAG}" --json url -q .url
fi

echo "[build-release-apk] done."
