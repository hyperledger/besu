#!/bin/bash
##
## Copyright contributors to Besu.
##
## Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
## an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
## specific language governing permissions and limitations under the License.
##
## SPDX-License-Identifier: Apache-2.0
##

set -euo pipefail

LINEA_BESU_VERSION=${1:?Must specify Linea Besu version}
TAR_DIST=${2:?Must specify path to tar distribution}
ZIP_DIST=${3:?Must specify path to zip distribution}

ENV_DIR=./build/tmp/cloudsmith-env
if [[ -d ${ENV_DIR} ]] ; then
    source ${ENV_DIR}/bin/activate
else
    python3 -m venv ${ENV_DIR}
    source ${ENV_DIR}/bin/activate
fi

python3 -m pip install --upgrade cloudsmith-cli

cloudsmith push raw consensys/linea-besu $TAR_DIST --republish --name 'linea-besu.tar.gz' --version "${LINEA_BESU_VERSION}" --summary "Linea Besu ${LINEA_BESU_VERSION} binary distribution" --description "Binary distribution of Linea Besu ${LINEA_BESU_VERSION}." --content-type 'application/tar+gzip'
cloudsmith push raw consensys/linea-besu $ZIP_DIST --republish --name 'linea-besu.zip' --version "${LINEA_BESU_VERSION}" --summary "Linea Besu ${LINEA_BESU_VERSION} binary distribution" --description "Binary distribution of Linea Besu ${LINEA_BESU_VERSION}." --content-type 'application/zip'
