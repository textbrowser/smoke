#!/bin/bash
# Alexis Megas, 2020.

declare -a j=0

# Replace contents of sizes with relevant values.

declare -a sizes=(4200090
		  4727417
		  4703426
		  4849242
		  4842605)
declare -a total=0

for i in ${sizes[@]}
do
    j=$(expr $j + 1)
    dd bs=1 count=$i if=output of=file$j skip=$total status='progress'
    total=$(expr $i + $total)
done
