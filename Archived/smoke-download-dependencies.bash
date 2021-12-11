#!/bin/bash

# Must be executed in the top-level source directory.

# Bouncy Castle

bouncycastle1=bcprov-ext-jdk15on-abc.jar

rm -f $bouncycastle1
wget --progress=bar https://bouncycastle.org/download/$bouncycastle1

if [ -r "$bouncycastle1" ]; then
    mv $bouncycastle1 Smoke/app/libs/.
else
    echo "Cannot read $bouncycastle1."
fi

echo "Please review Smoke/app/build.gradle and Smoke/app/libs!"
