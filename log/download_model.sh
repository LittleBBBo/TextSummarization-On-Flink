#!/usr/bin/env bash

# script to download pretrained model from Google Drive
#
# not guaranteed to work indefinitely
# taken from Stack Overflow answer:
# http://stackoverflow.com/a/38937732/7002068

#gURL='https://drive.google.com/uc?id=0B7pQmm-OfDv7ZUhHZm9ZWEZidDg&export=download'

# match id, more than 26 word characters
#ggID=$(echo "$gURL" | egrep -o '(\w|-){26,}')

ggID='0B7pQmm-OfDv7ZUhHZm9ZWEZidDg'

ggURL='https://drive.google.com/uc?export=download'
gURL="${ggURL}&id=${ggID}"

curl -sc /tmp/gcokie "${ggURL}&id=${ggID}" >/dev/null
getcode="$(awk '/_warning_/ {print $NF}' /tmp/gcokie)"

cmd='curl --insecure -C - -LOJb /tmp/gcokie "${ggURL}&confirm=${getcode}&id=${ggID}"'
echo -e "Downloading from "$gURL"...\n"
eval $cmd

# unzip data file
unzip pretrained_model_tf1.2.1.zip
rm pretrained_model_tf1.2.1.zip