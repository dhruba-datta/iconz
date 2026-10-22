#!/usr/bin/env bash
# Publish a new Iconz version (same flow as Wallet).
#
#   ./release.sh 0.2.0 "Added Upay, NexusPay and Cellfin"
#
# Regenerates the icon files, bumps versionName/versionCode in app/build.gradle.kts, builds
# a release APK signed with Iconz's release key, checks the signature, and creates GitHub
# release v<version> in the public releases repo with iconz.apk and latest.json. The app's
# Check for updates reads latest.json from there.
set -euo pipefail

REPO=dhruba-datta/iconz
# SHA-256 of the release signing certificate (../local/keys/iconz-release.jks). Android only
# installs an update signed with the same key, so never publish anything else.
CERT_SHA256=06e526fa283b9c5ba93c4074532c4780fe3528a9f838b3174bdd5b8a33588bd6

VERSION=${1:?usage: ./release.sh <version> "<release notes>"}
NOTES=${2:?release notes are shown in the app before updating; please give some}
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.3"; exit 1; }

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

cd "$(dirname "$0")"
OUT=../local/builds/release
[[ -f key.properties ]] || { echo "key.properties missing: copy ../local/keys/key.properties here"; exit 1; }

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
./gradlew --console=plain assembleRelease -Piconz.releasesRepo="$REPO"

APK=app/build/outputs/apk/release/app-release.apk
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -q "SHA-256 digest: $CERT_SHA256" \
  || { echo "✗ APK is not signed with Iconz's release key; not publishing"; exit 1; }
mkdir -p "$OUT"
cp "$APK" "$OUT/iconz.apk"
cp "$APK" "$OUT/Iconz-$VERSION.apk"

VERSION="$VERSION" NOTES="$NOTES" SIZE=$(stat -f%z "$OUT/iconz.apk") python3 -c '
import json, os, datetime
print(json.dumps({"version": os.environ["VERSION"], "notes": os.environ["NOTES"],
                  "size": int(os.environ["SIZE"]),
                  "date": datetime.date.today().isoformat()}))' > "$OUT/latest.json"

gh release create "v$VERSION" "$OUT/iconz.apk" "$OUT/latest.json" --repo "$REPO" \
  --title "Iconz $VERSION" --notes "$NOTES

Signing certificate SHA-256: \`$CERT_SHA256\`"
echo "✓ Published v$VERSION. Commit app/build.gradle.kts."
