#!/bin/bash
# Installs (or updates) Kestrel Isle on an Apple Silicon Mac, then opens it.
#   curl -fsSL https://raw.githubusercontent.com/MartelDanger3232/kestrel-isle/main/install-mac.sh | bash
# Downloading with curl rather than a browser means macOS doesn't block the un-notarized app.
set -e
url=https://github.com/MartelDanger3232/kestrel-isle/releases/latest/download/KestrelIsle-Mac-AppleSilicon.zip
if [ "$(uname -m)" != arm64 ]; then
    echo "This Mac has an Intel chip, but the Mac download is built for Apple Silicon (M1 and newer)."
    echo "Instead: install Java 22 or newer from https://adoptium.net, download kestrel-isle.jar from"
    echo "https://github.com/MartelDanger3232/kestrel-isle/releases/latest and run:  java -jar kestrel-isle.jar"
    exit 1
fi
echo "Downloading Kestrel Isle..."
curl -fL --progress-bar "$url" -o /tmp/KestrelIsle.zip
mkdir -p ~/Applications
rm -rf ~/Applications/"Kestrel Isle.app"
ditto -x -k /tmp/KestrelIsle.zip ~/Applications
rm -f /tmp/KestrelIsle.zip
echo "Installed in your Applications folder (~/Applications). Opening it now; next time, open it from Launchpad."
open ~/Applications/"Kestrel Isle.app"
