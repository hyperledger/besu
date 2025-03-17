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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;

public class FinishPosSyncStep implements Consumer<List<BlockHeader>> {

  private final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;

  public FinishPosSyncStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final BlockHeader pivotHeader,
      final boolean transactionIndexingEnabled) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public void accept(final List<BlockHeader> blocksWithReceipts) {
    //
  }

  @VisibleForTesting
  protected static long getBlocksPercent(final long lastBlock, final long totalBlocks) {
    if (totalBlocks == 0) {
      return 0;
    }
    return (100 * lastBlock / totalBlocks);
  }

  protected boolean importBlock(final SyncBlockWithReceipts blockWithReceipts) {
    final BlockImporter importer =
        protocolSchedule.getByBlockHeader(blockWithReceipts.getHeader()).getBlockImporter();
    final BlockImportResult blockImportResult =
        importer.importSyncBlockForSyncing(
            protocolContext, blockWithReceipts.getBlock(), blockWithReceipts.getReceipts(), false);
    return blockImportResult.isImported();
  }
}
