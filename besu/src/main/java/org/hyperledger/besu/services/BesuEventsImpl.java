/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.services.BesuEvents;

import java.util.function.Supplier;

public class BesuEventsImpl implements BesuEvents {
  private final BlockBroadcaster blockBroadcaster;
  private final TransactionPool transactionPool;
  private final SyncState syncState;

  public BesuEventsImpl(
      final BlockBroadcaster blockBroadcaster,
      final TransactionPool transactionPool,
      final SyncState syncState) {
    this.blockBroadcaster = blockBroadcaster;
    this.transactionPool = transactionPool;
    this.syncState = syncState;
  }

  @Override
  public long addBlockPropagatedListener(final BlockPropagatedListener listener) {
    return blockBroadcaster.subscribePropagateNewBlocks(
        (block, totalDifficulty) ->
            listener.onBlockPropagated(
                blockPropagatedContext(block::getHeader, () -> totalDifficulty)));
  }

  @Override
  public void removeBlockPropagatedListener(final long listenerIdentifier) {
    blockBroadcaster.unsubscribePropagateNewBlocks(listenerIdentifier);
  }

  @Override
  public long addTransactionAddedListener(final TransactionAddedListener listener) {
    return transactionPool.subscribePendingTransactions(listener::onTransactionAdded);
  }

  @Override
  public void removeTransactionAddedListener(final long listenerIdentifier) {
    transactionPool.unsubscribePendingTransactions(listenerIdentifier);
  }

  @Override
  public long addTransactionDroppedListener(
      final TransactionDroppedListener transactionDroppedListener) {
    return transactionPool.subscribeDroppedTransactions(
        transactionDroppedListener::onTransactionDropped);
  }

  @Override
  public void removeTransactionDroppedListener(final long listenerIdentifier) {
    transactionPool.unsubscribeDroppedTransactions(listenerIdentifier);
  }

  @Override
  public long addSyncStatusListener(final SyncStatusListener syncStatusListener) {
    return syncState.subscribeSyncStatus(syncStatusListener);
  }

  @Override
  public void removeSyncStatusListener(final long listenerIdentifier) {
    syncState.unsubscribeSyncStatus(listenerIdentifier);
  }

  private static PropagatedBlockContext blockPropagatedContext(
      final Supplier<BlockHeader> blockHeaderSupplier,
      final Supplier<Quantity> totalDifficultySupplier) {
    return new PropagatedBlockContext() {
      @Override
      public BlockHeader getBlockHeader() {
        return blockHeaderSupplier.get();
      }

      @Override
      public Quantity getTotalDifficulty() {
        return totalDifficultySupplier.get();
      }
    };
  }
}
