#!/bin/bash
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