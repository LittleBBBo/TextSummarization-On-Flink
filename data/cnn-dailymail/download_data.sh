#!/usr/bin/env bash

# script to download processed data from Google Drive
#
# not guaranteed to work indefinitely
# taken from Stack Overflow answer:
# http://stackoverflow.com/a/38937732/7002068

#gURL='https://drive.google.com/uc?id=0BzQ6rtO2VN95a0c3TlZCWkl3aU0&export=download'

# match id, more than 26 word characters
#ggID=$(echo "$gURL" | egrep -o '(\w|-){26,}')

ggID='0BzQ6rtO2VN95a0c3TlZCWkl3aU0'

ggURL='https://drive.google.com/uc?export=download'
gURL="${ggURL}&id=${ggID}"

curl -sc /tmp/gcokie "${ggURL}&id=${ggID}" >/dev/null
getcode="$(awk '/_warning_/ {print $NF}' /tmp/gcokie)"

cmd='curl --insecure -C - -LOJb /tmp/gcokie "${ggURL}&confirm=${getcode}&id=${ggID}"'
echo -e "Downloading from "$gURL"...\n"
eval $cmd

# unzip data file
unzip finished_files.zip
rm finished_files.zip