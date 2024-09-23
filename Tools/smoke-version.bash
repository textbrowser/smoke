#!/usr/bin/env bash

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Please specify the version: $0 <VERSION>."
    exit 1
fi

FILE="Smoke/app/build.gradle"

sed -i 's/\(versionCode \)[0-9]\+\([0-9]\+\)*/\1'"${VERSION//./}"'/' $FILE
sed -i 's/\(versionName "\)[0-9]\+\(\.[0-9]\+\)*"/\1'"$VERSION"'"/' $FILE

FILE="Smoke/app/src/main/java/org/purple/smoke/About.java"

sed -i 's/\(Smoke Version \)[0-9]\+\(\.[0-9]\+\)*/\1'"$VERSION"'/' $FILE
echo "Please modify the release notes!"
