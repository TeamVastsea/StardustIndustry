#!/usr/bin/env bash
# Downloads the highlight-tooltip mod jars that build.gradle expects under libs/.
#
# These jars are intentionally NOT committed: they are third-party binaries that
# move independently of our source, and libs/ is gitignored so a checkout stays
# small. CI therefore has to fetch them before ./gradlew build, and this script
# is that one step, so the local and CI setups cannot drift.
#
# The file names on the right match the coordinates in build.gradle exactly;
# Modrinth sometimes spells them with underscores, so each download is written
# out with an explicit -o name rather than trusting the server's filename.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p libs

fetch() {
  local url="$1" dest="$2"
  if [ -f "libs/$dest" ]; then
    echo "libs/$dest already present, skipping"
    return
  fi
  echo "Downloading $dest"
  curl -fsSL -A "stardustindustry-build" "$url" -o "libs/$dest"
}

# Jade: discovered via its @WailaPlugin annotation.
fetch "https://cdn.modrinth.com/data/nvQzSEkH/versions/eYz2YBGT/Jade-1.21.1-NeoForge-15.10.6.jar" \
      "jade-1.21.1-neoforge-15.10.6.jar"

# WTHIT (+ its hard dependency Bad Packets): discovered via waila_plugins.json.
fetch "https://cdn.modrinth.com/data/6AQIaxuO/versions/IeXnWQRE/wthit-1.21.1-neo-12.10.2.jar" \
      "wthit-1.21.1-neo-12.10.2.jar"
fetch "https://cdn.modrinth.com/data/ftdbN0KK/versions/RNyYl9M3/badpackets-neo-0.8.2.jar" \
      "badpackets-neo-0.8.2.jar"

# The One Probe: discovered via the getTheOneProbe inter-mod message.
fetch "https://cdn.modrinth.com/data/Eyw0UxEx/versions/4bbMy0Mh/theoneprobe-1.21_neo-12.0.8.jar" \
      "theoneprobe-1.21-neo-12.0.8.jar"

# Mekanism: optional chemical bridge (soft dependency). Needed at compile time
# for compat/mekanism; at runtime a pack may or may not ship it.
fetch "https://cdn.modrinth.com/data/Ce6I4WUE/versions/5KzzycBT/Mekanism-1.21.1-10.7.19.85.jar" \
      "Mekanism-1.21.1-10.7.19.85.jar"

echo "Highlight-tooltip + Mekanism libs ready in libs/"
