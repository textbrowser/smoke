#!/bin/bash

# Must be executed in the top-level source directory.

# Bouncy Castle

bouncycastle1=bcprov-ext-jdk15on-165.jar
bouncycastle2=bcprov-jdk15on-165.tar.gz

wget --progress=bar https://bouncycastle.org/download/$bouncycastle1
wget --progress=bar https://bouncycastle.org/download/$bouncycastle2
mv $bouncycastle1 Smoke/app/libs/.
mv $bouncycastle2 Smoke/app/libs/.
echo "Please review Smoke/app/build.gradle!" 
