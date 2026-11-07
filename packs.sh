#!/bin/bash
# Fetches the free (CC0) KayKit model packs by Kay Lousberg (www.kaylousberg.com) into packs/: Medieval Hexagon (the town
# buildings) and Dungeon Remastered. Only the glTF models and their textures come down, each from a pinned commit, and
# every file is checked against packs.sha256.
set -e
cd "$(dirname "$0")"
source() {
    case "$1" in
        dungeon) echo KayKit-Dungeon-Remastered-1.0/b0ca9bd96a8072ab36a3a5464f00ed1e06a16d07 ;;
        medieval) echo KayKit-Medieval-Hexagon-Pack-1.0/84fa4e91af6a88989be7c99e0891cede11f2ca38 ;;
    esac
}
# packs.sha256 lists every file as packs/<pack>/<path in that pack's repository>.
cut -c67- packs.sha256 | while read -r f; do
    [ -s "$f" ] && continue
    pack=$(echo "$f" | cut -d/ -f2)
    echo "https://raw.githubusercontent.com/KayKit-Game-Assets/$(source "$pack")/${f#packs/$pack/} $f"
done | xargs -P 8 -L 1 sh -c 'mkdir -p "$(dirname "$1")" && curl -fsSL "$0" -o "$1"'
if command -v sha256sum >/dev/null; then sha256sum -c --quiet packs.sha256; else shasum -a 256 -c --quiet packs.sha256; fi
