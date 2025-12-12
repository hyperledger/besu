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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportSnapSyncBlocksStep implements Consumer<List<SyncBlockWithReceipts>> {
  private static final Logger LOG = LoggerFactory.getLogger(ImportSnapSyncBlocksStep.class);
  private static final int PRINT_DELAY_SECONDS = 30;

  protected final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final long startBlock;
  private final boolean transactionIndexingEnabled;
  private final AtomicBoolean shouldLog = new AtomicBoolean(true);
  private final long pivotHeaderNumber;
  private final MutableBlockchain blockchain;

  public ImportSnapSyncBlocksStep(
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final long startBlock,
      final BlockHeader pivotHeader,
      final boolean transactionIndexingEnabled) {
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.startBlock = startBlock;
    this.pivotHeaderNumber = pivotHeader.getNumber();
    this.transactionIndexingEnabled = transactionIndexingEnabled;
    this.blockchain = protocolContext.getBlockchain();
  }

  @Override
  public void accept(final List<SyncBlockWithReceipts> blocksWithReceipts) {
    blockchain.unsafeImportSyncBodiesAndReceipts(blocksWithReceipts, transactionIndexingEnabled);
    final SyncBlockWithReceipts lastBlock = blocksWithReceipts.getLast();
    LOG.atTrace()
        .setMessage("Imported blocks up to {}")
        .addArgument(lastBlock.getBlock()::toLogString)
        .log();

    final long lastBlockNumber = lastBlock.getNumber();

    syncState.setSyncProgress(startBlock, lastBlockNumber, pivotHeaderNumber);

    if (shouldLog.get()) {
      int peerCount = -1; // ethContext is not available in tests
      if (ethContext != null && ethContext.getEthPeers().peerCount() >= 0) {
        peerCount = ethContext.getEthPeers().peerCount();
      }
      final long blocksPercent = getBlocksPercent(lastBlockNumber, pivotHeaderNumber);
      throttledLog(
          LOG::info,
          String.format(
              "Block import progress: %s of %s (%s%%), Peer count: %s",
              lastBlockNumber, pivotHeaderNumber, blocksPercent, peerCount),
          shouldLog,
          PRINT_DELAY_SECONDS);
    }
  }

  @VisibleForTesting
  protected static long getBlocksPercent(final long lastBlock, final long totalBlocks) {
    if (totalBlocks == 0) {
      return 0;
    }
    return (100 * lastBlock / totalBlocks);
  }
}
