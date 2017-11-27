#!/bin/bash
echo running $1 routers
for ((i=0; i<$1; i++));	do
	gnome-terminal -e "./single.sh $i" &
done
exit

