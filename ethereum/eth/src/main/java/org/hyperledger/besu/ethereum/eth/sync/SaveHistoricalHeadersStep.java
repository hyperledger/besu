/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaveHistoricalHeadersStep implements Function<BlockHeader, Stream<BlockHeader>> {
  private static final Logger LOG = LoggerFactory.getLogger(SaveHistoricalHeadersStep.class);
  private final MutableBlockchain blockchain;
  private final long threshold;
  private final AtomicBoolean logInfo = new AtomicBoolean(true);
  private static final int LOG_REPEAT_DELAY = 10;

  public SaveHistoricalHeadersStep(final MutableBlockchain blockchain, final long threshold) {
    this.blockchain = blockchain;
    this.threshold = threshold;
  }

  @Override
  public Stream<BlockHeader> apply(final BlockHeader blockHeader) {
    if (threshold > 0) {
      if (blockHeader.getNumber() < threshold) {
        savePreMergeBlockHeader(blockHeader);
        return Stream.empty();
      }
      if (blockHeader.getNumber() == threshold) {
        saveMergeBlockHeader(blockHeader);
        return Stream.empty();
      }
    }
    return Stream.of(blockHeader);
  }

  private void savePreMergeBlockHeader(final BlockHeader blockHeader) {
    blockchain.storeHeaderUnsafe(blockHeader, Optional.empty());
    logProgress(blockHeader);
  }

  private void saveMergeBlockHeader(final BlockHeader blockHeader) {
    blockchain.storeHeaderUnsafe(blockHeader, Optional.of(Difficulty.ZERO));
    blockchain.unsafeSetChainHead(blockHeader, Difficulty.ZERO);
    LOG.info("Pre merge block headers import completed at block {}", blockHeader.toLogString());
  }

  private void logProgress(final BlockHeader blockHeader) {
    if (blockHeader.getNumber() % 100 == 0) {
      double importPercent = (double) (100 * blockHeader.getNumber()) / threshold;
      throttledLog(
          LOG::info,
          String.format(
              "Pre merge block headers import progress: %d of %d (%.2f%%)",
              blockHeader.getNumber(), threshold, importPercent),
          logInfo,
          LOG_REPEAT_DELAY);
    }
  }
}
