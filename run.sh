#!/bin/sh
# Builds and launches Kestrel Isle full screen (Esc pauses, then Q quits). Extra flags: --dev-window, --classic (the old
# software renderer), --test, --bench, --shots DIR, --gpu-shots DIR
cd "$(dirname "$0")" && bash deps.sh && bash packs.sh && rm -rf out && javac -d out -cp "lib/*" src/kestrel/*.java && cp -R res/. out/ \
    && exec java --enable-native-access=ALL-UNNAMED -cp "out:lib/*" kestrel.Main "$@"
