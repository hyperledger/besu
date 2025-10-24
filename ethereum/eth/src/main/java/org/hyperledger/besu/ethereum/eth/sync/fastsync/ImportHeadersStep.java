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
  private final long downloaderHeaderTarget;
  private final long pivotBlockNumber;
  private final AtomicBoolean logInfo = new AtomicBoolean(true);
  private final FastSyncState fastSyncState;
  private BlockHeader currentChildHeader;

  public ImportHeadersStep(
      final MutableBlockchain blockchain,
      final long downloaderHeaderTarget,
      final FastSyncState fastSyncState) {
    this.blockchainStorage = blockchain;
    this.downloaderHeaderTarget = downloaderHeaderTarget;
    this.fastSyncState = fastSyncState;
    this.pivotBlockNumber = fastSyncState.getPivotBlockNumber().getAsLong();
    this.currentChildHeader = fastSyncState.getPivotBlockHeader().get();
  }

  @Override
  public void accept(final List<BlockHeader> blockHeaders) {
    if (!blockHeaders.getFirst().getHash().equals(currentChildHeader.getParentHash())) {
      LOG.info(
          "Received invalid header list (expected hash {} for Block {}, but got {})",
          currentChildHeader.getParentHash(),
          blockHeaders.getFirst().getNumber(),
          blockHeaders.getFirst().getHash());
      throw new IllegalStateException(
          "Received header with unexpected parent hash, expected "
              + currentChildHeader.getParentHash()
              + ", got "
              + blockHeaders.getFirst().getHash());
    }
    currentChildHeader = blockHeaders.getLast();
    fastSyncState.setCurrentHeader(
        currentChildHeader); // make sure we restart from here in case of failure
    blockHeaders.forEach(blockchainStorage::importHeader);

    final long totalHeaders = pivotBlockNumber - downloaderHeaderTarget;
    final long downloadedHeaders =
        totalHeaders - (blockHeaders.getFirst().getNumber() - downloaderHeaderTarget);
    final double headersPercent = (double) (downloadedHeaders) / totalHeaders * 100;
    LogUtil.throttledLog(
        LOG::info,
        String.format("Header import progress %.2f%%", headersPercent),
        logInfo,
        LOG_DELAY);
  }
}
