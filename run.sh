#!/bin/sh
# Builds and launches Kestrel Isle full screen (Esc pauses, then Q quits). Extra flags: --res 480, --test, --bench, --shots DIR
cd "$(dirname "$0")" && rm -rf out && javac -d out src/kestrel/*.java && exec java --enable-native-access=ALL-UNNAMED -cp out kestrel.Main "$@"

