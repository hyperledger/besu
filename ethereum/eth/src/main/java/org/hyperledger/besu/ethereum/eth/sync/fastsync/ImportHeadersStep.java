/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.util.log.LogUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportHeadersStep implements Consumer<List<BlockHeader>> {

  private static final Logger LOG = LoggerFactory.getLogger(ImportHeadersStep.class);
  private static final int LOG_DELAY = 30;

  private final MutableBlockchain blockchainStorage;
  private final long lowestHeaderToImport;
  private final AtomicBoolean logInfo = new AtomicBoolean(true);
    private final Hash checkpointBlockHash;
    private BlockHeader currentChildHeader;
  private final long totalHeaders;

  public ImportHeadersStep(
          final MutableBlockchain blockchain,
          final long checkpointBlockNumber,
          final Hash checkpointBlockHash,
          final BlockHeader pivotBlockHeader) {
    this.blockchainStorage = blockchain;
    this.lowestHeaderToImport = checkpointBlockNumber + 1;
      this.checkpointBlockHash = checkpointBlockHash;
      final long pivotBlockNumber = pivotBlockHeader.getNumber();
    this.currentChildHeader = pivotBlockHeader;
    this.totalHeaders = pivotBlockNumber - checkpointBlockNumber;
    // store the pivot block header as the first imported header
    this.blockchainStorage.importHeader(pivotBlockHeader);
  }

  @Override
  public void accept(final List<BlockHeader> blockHeaders) {
    if (!blockHeaders.getFirst().getHash().equals(currentChildHeader.getParentHash())) {
      String message =
          "Received invalid header list: expected hash "
              + currentChildHeader.getParentHash()
              + "  for highest Block number "
              + blockHeaders.getFirst().getNumber()
              + " ,but got "
              + blockHeaders.getFirst().getHash();
      LOG.info(message);
      throw new IllegalStateException(message);
    }

    currentChildHeader = blockHeaders.getLast();
    if (currentChildHeader.getNumber() == lowestHeaderToImport) {
        if (!currentChildHeader.getParentHash().equals(checkpointBlockHash)) {
            throw new IllegalStateException("The lower header parent hash does not match the checkpoint hash");
        }
    }
    blockHeaders.forEach(blockchainStorage::importHeader);

    if (logInfo.get()) {
      final long downloadedHeaders =
          totalHeaders - (blockHeaders.getFirst().getNumber() - lowestHeaderToImport);
      final double headersPercent = (double) (downloadedHeaders) / totalHeaders * 100;
      LogUtil.throttledLog(
          LOG::info,
          String.format("Header import progress %.2f%%", headersPercent),
          logInfo,
          LOG_DELAY);
    }
  }
}
