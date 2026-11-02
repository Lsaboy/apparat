#!/usr/bin/env bash
# Builds desktop/build/Apparat.flatpak. Needs: JDK 17+, flatpak, flatpak-builder and the Flathub
# remote (flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
./gradlew -p desktop createDistributable
OUT="$ROOT/desktop/build/flatpak"
mkdir -p "$OUT"
flatpak-builder --user --force-clean --install-deps-from=flathub \
  --repo="$OUT/repo" "$OUT/build-dir" desktop/packaging/flatpak/com.omniforge.Apparat.yml
flatpak build-bundle "$OUT/repo" "$ROOT/desktop/build/Apparat.flatpak" com.omniforge.Apparat \
  --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
echo "Built $ROOT/desktop/build/Apparat.flatpak"
echo "Install with: flatpak install --user $ROOT/desktop/build/Apparat.flatpak"
