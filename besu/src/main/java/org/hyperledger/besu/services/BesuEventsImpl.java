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

import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.UnformattedData;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toUnmodifiableList;

public class BesuEventsImpl implements BesuEvents {
  private Blockchain blockchain;
  private final BlockBroadcaster blockBroadcaster;
  private final TransactionPool transactionPool;
  private final SyncState syncState;

  public BesuEventsImpl(
      final Blockchain blockchain,
      final BlockBroadcaster blockBroadcaster,
      final TransactionPool transactionPool,
      final SyncState syncState) {
    this.blockchain = blockchain;
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

  @Override
  public long addLogListener(
      List<Address> addresses, List<List<UnformattedData>> topics, LogListener logListener) {
    final List<org.hyperledger.besu.ethereum.core.Address> besuAddresses =
        addresses.stream()
            .map(
                address ->
                    org.hyperledger.besu.ethereum.core.Address.wrap(
                        BytesValue.wrap(address.getByteArray())))
            .collect(toUnmodifiableList());
    final List<List<LogTopic>> besuTopics =
        topics.stream()
            .map(
                subList ->
                    subList.stream()
                        .map(bytes -> LogTopic.create(BytesValue.wrap(bytes.getByteArray())))
                        .collect(toUnmodifiableList()))
            .collect(toUnmodifiableList());

    final LogsQuery logsQuery = new LogsQuery(besuAddresses, besuTopics);

    return blockchain.addLogListener(
        logWithMetadata -> {
          if (logsQuery.matches(logWithMetadata)) {
            logListener.onLogEmitted(logWithMetadata);
          }
        });
  }

  @Override
  public void removeLogListener(long listenerIdentifier) {}

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
