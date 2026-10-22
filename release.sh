#!/usr/bin/env bash
# Publish a new Iconz version.
#
#   ./release.sh 0.3.0 "Added Upay, NexusPay and Cellfin"
#
# Regenerates the icon files, bumps versionName/versionCode in app/build.gradle.kts, builds
# a release APK signed with Iconz's release key, checks it has no permissions and the right
# signature, and creates GitHub release v<version> with iconz.apk. The README's download
# link always gives the latest release; it installs over the previous one.
set -euo pipefail

REPO=dhruba-datta/iconz
# SHA-256 of the release signing certificate (../local/keys/iconz-release.jks). Android only
# installs a new version over the old one if it is signed with the same key.
CERT_SHA256=06e526fa283b9c5ba93c4074532c4780fe3528a9f838b3174bdd5b8a33588bd6

VERSION=${1:?usage: ./release.sh <version> "<release notes>"}
NOTES=${2:?please give release notes}
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.3"; exit 1; }

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

cd "$(dirname "$0")"
OUT=../local/builds/release
[[ -f key.properties ]] || { echo "key.properties missing: copy ../local/keys/key.properties here"; exit 1; }
# The release is tagged at the commit that built it, so start from committed work.
[[ -z $(git status --porcelain) ]] || { echo "commit your changes first"; exit 1; }

latest=$(gh release view --repo "$REPO" --json tagName -q .tagName 2>/dev/null || echo v0.0.0)
newest=$(printf '%s\n%s\n' "${latest#v}" "$VERSION" | sort -V | tail -1)
if [[ $newest != "$VERSION" || ${latest#v} == "$VERSION" ]]; then
  echo "v$VERSION is not newer than the latest release $latest"; exit 1
fi

GRADLE=app/build.gradle.kts
code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' $GRADLE)
sed -i '' -e "s/^\( *versionCode = \).*/\1$((code + 1))/" -e "s/^\( *versionName = \).*/\1\"$VERSION\"/" $GRADLE
echo "→ version $VERSION (build $((code + 1)))"

python3 tool/build_icons.py
python3 tool/check_icons.py
./gradlew --console=plain clean assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -q "SHA-256 digest: $CERT_SHA256" \
  || { echo "✗ APK is not signed with Iconz's release key; not publishing"; exit 1; }
# Iconz must never ask for a permission: that is what got it flagged as harmful before.
perms=$("$BUILD_TOOLS/aapt2" dump permissions "$APK" | grep uses-permission | grep -v DYNAMIC_RECEIVER_NOT_EXPORTED || true)
[[ -z $perms ]] || { echo "✗ APK asks for permissions:"; echo "$perms"; exit 1; }

mkdir -p "$OUT"
cp "$APK" "$OUT/iconz.apk"
cp "$APK" "$OUT/Iconz-$VERSION.apk"

git add "$GRADLE" app/src/main/res/xml app/src/main/assets
git commit -q -m "Release $VERSION"
git push -q
# --target: tag exactly the commit just pushed (GitHub needs the full sha).
gh release create "v$VERSION" "$OUT/iconz.apk" --repo "$REPO" --target "$(git rev-parse HEAD)" \
  --title "Iconz $VERSION" --notes "$NOTES

Signing certificate SHA-256: \`$CERT_SHA256\`"
git fetch -q --tags
echo "✓ Published v$VERSION."
