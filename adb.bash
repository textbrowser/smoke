#!/usr/bin/env bash

adb="${HOME}/Android/Sdk/platform-tools/adb"

$adb devices | sort -u | grep 'device$' | while read line
do
    device=$(echo $line | awk '{print $1}')
    $adb -s $device $@ &
    wait
done
