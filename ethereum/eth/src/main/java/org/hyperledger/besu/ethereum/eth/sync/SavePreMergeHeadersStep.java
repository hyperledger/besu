/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step in the synchronization process that saves historical block headers. */
public class SavePreMergeHeadersStep implements Function<BlockHeader, Stream<BlockHeader>> {
  private static final Logger LOG = LoggerFactory.getLogger(SavePreMergeHeadersStep.class);
  private final MutableBlockchain blockchain;
  private final long mergeBlockNumber;

  private final AtomicBoolean shouldLog = new AtomicBoolean(true);
  private static final int LOG_REPEAT_DELAY_SECONDS = 30;
  private static final int LOG_PROGRESS_INTERVAL = 1000;

  public SavePreMergeHeadersStep(final MutableBlockchain blockchain, final long mergeBlockNumber) {
    this.blockchain = blockchain;
    this.mergeBlockNumber = mergeBlockNumber;
  }

  @Override
  public Stream<BlockHeader> apply(final BlockHeader blockHeader) {
    long blockNumber = blockHeader.getNumber();
    if (isPostMergeBlock(blockNumber)) {
      return Stream.of(blockHeader);
    }
    storeBlockHeader(blockHeader);
    logProgress(blockHeader);
    return Stream.empty();
  }

  private boolean isPostMergeBlock(final long blockNumber) {
    return blockNumber >= mergeBlockNumber;
  }

  private void storeBlockHeader(final BlockHeader blockHeader) {
    Difficulty difficulty = blockchain.calculateTotalDifficulty(blockHeader);
    blockchain.unsafeStoreHeader(blockHeader, difficulty);
  }

  private void logProgress(final BlockHeader blockHeader) {
    long lastPoWBlockNumber = mergeBlockNumber - 1;
    if (blockHeader.getNumber() == lastPoWBlockNumber) {
      LOG.info("Pre-merge headers import completed at block {}", blockHeader.toLogString());
    } else {
      long blockNumber = blockHeader.getNumber();
      if (blockNumber % LOG_PROGRESS_INTERVAL == 0) {
        double importPercent = (double) (100 * blockNumber) / lastPoWBlockNumber;
        throttledLog(
            LOG::info,
            String.format(
                "Pre-merge headers import progress: %d of %d (%.2f%%)",
                blockNumber, lastPoWBlockNumber, importPercent),
            shouldLog,
            LOG_REPEAT_DELAY_SECONDS);
      }
    }
  }
}
