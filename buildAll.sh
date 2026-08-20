#!/usr/bin/env sh
# Builds one jar per supported Minecraft version into build/libs.
# Requires JDK 25 (26.x targets compile against Voxy classes built for Java 25).
set -e
for mc in 26.2 26.1.2; do
    echo "=== building for $mc"
    ./gradlew build -PmcVersion="$mc" "$@"
done
echo
ls -1 build/libs/
