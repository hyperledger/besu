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
package org.hyperledger.besu.ethereum.eth.sync.possync;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.util.log.LogUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportHeadersStep implements Consumer<List<BlockHeader>> {
  private static final Logger LOG = LoggerFactory.getLogger(ImportHeadersStep.class);
  private static final int LOG_DELAY = 30;

  private final MutableBlockchain blockchainStorage;
  private final Supplier<Long> pivotBlockNumber;
  private final AtomicBoolean logInfo = new AtomicBoolean(true);

  public ImportHeadersStep(
      final MutableBlockchain blockchain, final Supplier<Long> pivotBlockNumber) {
    this.blockchainStorage = blockchain;
    this.pivotBlockNumber = pivotBlockNumber;
  }

  @Override
  public void accept(final List<BlockHeader> blockHeaders) {
    blockHeaders.forEach(blockchainStorage::importHeader);

    final long lastHeader = blockHeaders.get(blockHeaders.size() - 1).getNumber();
    final long pivotBlock = pivotBlockNumber.get();
    final long blocksPercent = getBlocksPercent(lastHeader, pivotBlock);
    LogUtil.throttledLog(
        LOG::info,
        String.format(
            "Header import progress: {} of {} ({}%)", lastHeader, pivotBlock, blocksPercent),
        logInfo,
        LOG_DELAY);
  }

  protected static long getBlocksPercent(final long lastHeader, final long totalHeaders) {
    if (totalHeaders == 0) {
      return 0;
    }
    return (100 * lastHeader / totalHeaders);
  }
}
