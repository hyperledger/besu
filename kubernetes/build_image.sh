#!/bin/sh -e
# This script presents a sample way to build Pantheon Docker image
# with automatic build arguments from the current build workspace.
# It must be started from the same path as where the Dockerfile is located.
# you have to pass the imnage tag as an argument like for instance :
# build_image.sh "pegasyseng/pantheon-kubernetes:develop"

CONTEXT_FOLDER=kubernetes/
PANTHEON_BUILD_SOURCE='build/distributions/pantheon-*.tar.gz'

# Checking that you passed the tag for the image to be build
if [ -z "$1" ]
  then
    me=`basename "$0"`
    echo "No image tag argument supplied to ${me}"
    echo "ex.: ${me} \"pegasyseng/pantheon-kubernetes:develop\""
    exit 1
fi

# looking for the distribution archive, either form CI step that builds form this
# workspace sources but with multiple test steps first
# or it builds it if you don't have one as you are probably
# not in a CI step.
if ls ${PANTHEON_BUILD_SOURCE} 1> /dev/null 2>&1; then
    cp ${PANTHEON_BUILD_SOURCE} ${CONTEXT_FOLDER}
else
    echo "No pantheon-*.tar.gz archive found."
    echo "You are probably not running this from CI so running './gradlew distTar' first to have a local build"
    ./gradlew distTar
    cp ${PANTHEON_BUILD_SOURCE} ${CONTEXT_FOLDER}
fi

# Builds docker image with tags matching the info form this current workspace
docker build \
-t "$1" \
--build-arg BUILD_DATE="`date`" \
--build-arg VCS_REF="`git show -s --format=%h`" \
--build-arg VERSION="`grep -oE "version=(.*)" gradle.properties | cut -d= -f2`" \
${CONTEXT_FOLDER}
