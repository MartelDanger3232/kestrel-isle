#!/usr/bin/env bash
# Builds Kestrel Isle as a self-contained app for the computer this runs on (its own Java runtime included) into dist/,
# alongside a plain kestrel-isle.jar (engine included) for anyone who already has Java 22 or newer.
#   ./package.sh                         just build dist/ and the app in build/app
#   ./package.sh KestrelIsle-Mac.zip     also archive the app as dist/KestrelIsle-Mac.zip (.zip, or .tar.gz on Linux)
set -euo pipefail
cd "$(dirname "$0")"
archive="${1:-}"
bash deps.sh
bash packs.sh
rm -rf out build dist
mkdir -p build/input build/fat dist
javac --release 22 -d out -cp "lib/*" src/kestrel/*.java
# One jar with the game, its shaders and the whole engine, native libraries for every platform included.
for j in lib/*.jar; do (cd build/fat && jar -xf "../../$j"); done
rm -f build/fat/META-INF/MANIFEST.MF build/fat/META-INF/*.SF build/fat/META-INF/*.RSA build/fat/META-INF/*.DSA
find build/fat -name module-info.class -delete
cp -R out/. res/. THIRD-PARTY.md licenses build/fat/
# The town buildings come from the KayKit Medieval Hexagon pack.
mkdir -p build/fat/medieval/addons/kaykit_medieval_hexagon_pack
cp -R packs/medieval/addons/kaykit_medieval_hexagon_pack/. build/fat/medieval/addons/kaykit_medieval_hexagon_pack/
printf 'Main-Class: kestrel.Main\nEnable-Native-Access: ALL-UNNAMED\nMulti-Release: true\n' > build/manifest.txt
jar --create --file build/input/kestrel-isle.jar --manifest build/manifest.txt -C build/fat .
cp build/input/kestrel-isle.jar dist/

# The engine's window must own a Mac's first thread, and newer Javas want its low-level memory access allowed.
opts=(--java-options --enable-native-access=ALL-UNNAMED --java-options --sun-misc-unsafe-memory-access=allow)
case "$(uname -s)" in
    Darwin) name="Kestrel Isle"; opts+=(--java-options -XstartOnFirstThread) ;;
    *) name="KestrelIsle" ;;
esac
jpackage --type app-image --name "$name" --app-version 2.1 --vendor "Kestrel Isle" \
    --input build/input --main-jar kestrel-isle.jar --main-class kestrel.Main "${opts[@]}" --dest build/app
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
