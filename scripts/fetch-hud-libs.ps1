# Downloads extension compile/runtime jars that the Kotlin multi-project build
# expects under libs/.
#
# Windows twin of scripts/fetch-hud-libs.sh. The two must stay in step: the same
# jars and local file names, so a build behaves identically on a
# developer machine and on CI.
#
# These jars are intentionally not committed (libs/ is gitignored); a fresh
# checkout runs this once to make the extension compile dependencies resolvable.
param()
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root "libs"
New-Item -ItemType Directory -Force -Path $libs | Out-Null

function Fetch([string]$url, [string]$dest) {
    $target = Join-Path $libs $dest
    if (Test-Path $target) {
        Write-Output "libs/$dest already present, skipping"
        return
    }
    Write-Output "Downloading $dest"
    Invoke-WebRequest -Uri $url -OutFile $target -Headers @{ "User-Agent" = "stardustindustry-build" }
}

# Jade: discovered via its @WailaPlugin annotation.
Fetch "https://cdn.modrinth.com/data/nvQzSEkH/versions/eYz2YBGT/Jade-1.21.1-NeoForge-15.10.6.jar" `
      "jade-1.21.1-neoforge-15.10.6.jar"

# WTHIT (+ its hard dependency Bad Packets): discovered via waila_plugins.json.
Fetch "https://cdn.modrinth.com/data/6AQIaxuO/versions/IeXnWQRE/wthit-1.21.1-neo-12.10.2.jar" `
      "wthit-1.21.1-neo-12.10.2.jar"
Fetch "https://cdn.modrinth.com/data/ftdbN0KK/versions/RNyYl9M3/badpackets-neo-0.8.2.jar" `
      "badpackets-neo-0.8.2.jar"

# The One Probe: discovered via the getTheOneProbe inter-mod message.
Fetch "https://cdn.modrinth.com/data/Eyw0UxEx/versions/4bbMy0Mh/theoneprobe-1.21_neo-12.0.8.jar" `
      "theoneprobe-1.21-neo-12.0.8.jar"

# Mekanism: hard dependency of StardustIndustry-MekanismEx. Core never loads it.
Fetch "https://cdn.modrinth.com/data/Ce6I4WUE/versions/5KzzycBT/Mekanism-1.21.1-10.7.19.85.jar" `
      "Mekanism-1.21.1-10.7.19.85.jar"

# Applied Energistics 2: hard dependency of StardustIndustry-AE2Ex.
Fetch "https://cdn.modrinth.com/data/XxWD5pD3/versions/KDnFUmMm/appliedenergistics2-19.2.18.jar" `
      "appliedenergistics2-19.2.18.jar"

# AE2's in-game documentation library.
Fetch "https://cdn.modrinth.com/data/Ck4E7v7R/versions/hFpGwC6q/guideme-21.1.19.jar" `
      "guideme-21.1.19.jar"

Write-Output "Extension libraries ready in libs/"
