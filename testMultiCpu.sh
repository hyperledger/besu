#!/bin/bash
REPO=registry.gitlab.com/consensys/client/hong-kong/infra/docker
REPO_USER=pipeline
REPO_PASSWORD=${GITLAB_TOKEN}
BUILDX_ARCH="linux-amd64"
BUILDX_VERSION="v0.7.0"

#mkdir -p ~/.docker/cli-plugins
#wget -nv -O ~/.docker/cli-plugins/docker-buildx https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.${BUILDX_ARCH}  
#chmod a+x ~/.docker/cli-plugins/docker-buildx

curl -L https://github.com/regclient/regclient/releases/latest/download/regctl-${BUILDX_ARCH} >regctl
chmod 755 regctl

docker login registry.gitlab.com --username $REPO_USER --password $REPO_PASSWORD
            
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
docker buildx rm builderx

# Create a new docker context for builder instance to use
docker context create builder-context

# Create a builder instance named "builderx"
docker buildx create --name builderx --driver docker-container --use builder-context

docker buildx inspect --bootstrap

./gradlew --no-daemon "-Prepository=${REPO}" dockerUpload