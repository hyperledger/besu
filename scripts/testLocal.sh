#!/bin/bash
set -euxo pipefail

pushd ..

circleci config process .circleci/config.yml > process.yml
#circleci local execute -c process.yml --job $1

popd +0