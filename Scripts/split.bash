#!/bin/bash
# Alexis Megas, 2020.

# Expected input file name: output.
# Replace contents of sizes with relevant values.

declare -a sizes=(4200090
		  4727417
		  4703426
		  4849242
		  4842605)
declare -i j=0
declare -i total=0

for i in ${sizes[@]}
do
    j=$j+1
    dd bs=1 count=$i if=output of=file$j skip=$total status='progress'
    echo "SHA-256: " $(sha256sum file$j 2> /dev/null)
    total=$i+$total
done
