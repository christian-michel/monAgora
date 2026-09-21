#!/usr/bin/env bash
# Installe le SDK Android en ligne de commande (sans Android Studio) : de quoi
# compiler les futurs modules Android du projet (app/gps-citoyen et les autres).
#
# Nécessite un accès réseau à dl.google.com — dans un environnement Claude Code
# on the web, c'est un hôte à autoriser explicitement dans la politique réseau
# de l'environnement (voir https://code.claude.com/docs/en/claude-code-on-the-web).
#
# Usage : ANDROID_SDK_ROOT=/opt/android-sdk ./scripts/install-android-sdk.sh
# (variable optionnelle, /opt/android-sdk par défaut)

set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708" # cf. https://developer.android.com/studio#command-line-tools-only
COMPILE_SDK="34"
BUILD_TOOLS="34.0.0"

echo "Installation du SDK Android dans $SDK_ROOT"
mkdir -p "$SDK_ROOT/cmdline-tools"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    tmp_zip="$(mktemp)"
    curl -fSL -o "$tmp_zip" \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    tmp_extract="$(mktemp -d)"
    unzip -q "$tmp_zip" -d "$tmp_extract"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    mv "$tmp_extract/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$tmp_zip" "$tmp_extract"
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses > /dev/null

"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
    "platform-tools" \
    "build-tools;${BUILD_TOOLS}" \
    "platforms;android-${COMPILE_SDK}"

echo "sdk.dir=$SDK_ROOT" > local.properties

echo "OK — SDK installé dans $SDK_ROOT, local.properties écrit à la racine du projet."
echo "compileSdk=$COMPILE_SDK, build-tools=$BUILD_TOOLS — cohérent avec ce que déclarent"
echo "les modules Android du projet (à ajuster ensemble si l'un des deux change)."
