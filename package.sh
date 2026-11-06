#!/usr/bin/env bash
# Builds Kestrel Isle as a self-contained app for the computer this runs on (its own Java runtime included) into dist/,
# alongside a plain kestrel-isle.jar for anyone who already has Java 22 or newer.
#   ./package.sh                         just build dist/ and the app in build/app
#   ./package.sh KestrelIsle-Mac.zip     also archive the app as dist/KestrelIsle-Mac.zip (.zip, or .tar.gz on Linux)
set -euo pipefail
cd "$(dirname "$0")"
archive="${1:-}"
rm -rf out build dist
mkdir -p build/input dist
javac --release 22 -d out src/kestrel/*.java
printf 'Main-Class: kestrel.Main\nEnable-Native-Access: ALL-UNNAMED\n' > build/manifest.txt
jar --create --file build/input/kestrel-isle.jar --manifest build/manifest.txt -C out .
cp build/input/kestrel-isle.jar dist/

case "$(uname -s)" in
    Darwin) name="Kestrel Isle" ;;
    *) name="KestrelIsle" ;;
esac
jpackage --type app-image --name "$name" --app-version 1.0 --vendor "Kestrel Isle" \
    --input build/input --main-jar kestrel-isle.jar --main-class kestrel.Main \
    --java-options --enable-native-access=ALL-UNNAMED --dest build/app
[ -z "$archive" ] && exit 0

case "$(uname -s)" in
    Darwin)
        # One consistent ad-hoc signature, so a downloaded copy asks to be allowed instead of claiming to be damaged.
        codesign --force --deep --sign - "build/app/$name.app"
        ditto -c -k --keepParent "build/app/$name.app" "dist/$archive" ;;
    Linux)
        tar -czf "dist/$archive" -C build/app "$name" ;;
    *)
        powershell -NoProfile -Command "Compress-Archive -Path 'build/app/$name' -DestinationPath 'dist/$archive'" ;;
esac
echo "built dist/$archive"
