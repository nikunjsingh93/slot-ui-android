#!/usr/bin/env bash
# Downloads the libretro cores slot bundles (arm64) into app/src/main/jniLibs so Gradle can package them.
set -euo pipefail
dest="$(dirname "$0")/../app/src/main/jniLibs/arm64-v8a"
mkdir -p "$dest"
for core in mgba gpsp gambatte fceumm snes9x genesis_plus_gx mednafen_pce_fast; do
  tmp="$(mktemp -d)"
  curl -fsSL "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/${core}_libretro_android.so.zip" -o "$tmp/c.zip"
  unzip -q -o "$tmp/c.zip" -d "$tmp"
  cp "$tmp/${core}_libretro_android.so" "$dest/lib${core}_libretro_android.so"
  rm -rf "$tmp"
  echo "fetched $core"
done
