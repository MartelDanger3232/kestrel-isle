#!/bin/bash
# Fetches the engine the game draws with (jMonkeyEngine and LWJGL, both BSD-licensed) from Maven Central into lib/,
# then checks every file against the checksums in lib.sha256.
set -e
cd "$(dirname "$0")"
mkdir -p lib
repo=https://repo1.maven.org/maven2
jme=3.9.0-stable
lwjgl=3.3.6
jars=()
for a in jme3-core jme3-desktop jme3-lwjgl3 jme3-effects jme3-plugins jme3-plugins-json jme3-plugins-json-gson; do jars+=("org/jmonkeyengine/$a/$jme/$a-$jme.jar"); done
# The glTF model loader reads its JSON through Gson (Apache 2.0).
jars+=("com/google/code/gson/gson/2.9.1/gson-2.9.1.jar")
for a in lwjgl lwjgl-glfw lwjgl-jemalloc lwjgl-openal lwjgl-opengl lwjgl-opencl; do
    jars+=("org/lwjgl/$a/$lwjgl/$a-$lwjgl.jar")
    # OpenCL never needs a native library of its own, and the game makes its own sound, so OpenAL's classes are enough.
    [ $a = lwjgl-opencl ] || [ $a = lwjgl-openal ] && continue
    for os in windows linux linux-arm64 macos macos-arm64; do jars+=("org/lwjgl/$a/$lwjgl/$a-$lwjgl-natives-$os.jar"); done
done
for j in "${jars[@]}"; do
    f="lib/${j##*/}"
    [ -s "$f" ] || curl -fsSL "$repo/$j" -o "$f"
done
cd lib
if command -v sha256sum >/dev/null; then sha256sum -c --quiet ../lib.sha256; else shasum -a 256 -c --quiet ../lib.sha256; fi
