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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.LogWithMetadata;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.data.Transaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

/**
 * This service allows plugins to attach to various events during the normal operation of Besu.
 *
 * <p>Currently supported events
 *
 * <ul>
 *   <li><b>BlockAdded</b> - Fired when a new block has been evaluated and validated.
 *   <li><b>BlockReorg</b> - Fired when a block is removed from the chain to change to a different
 *       chainhead.
 *   <li><b>BlockPropagated</b> - Fired when a new block header has been received and validated and
 *       is about to be sent out to other peers, but before the body of the block has been evaluated
 *       and validated.
 *   <li><b>TransactionAdded</b> - Fired when a new transaction has been added to the node.
 *   <li><b>TransactionDropped</b> - Fired when a new transaction has been dropped from the node.
 *   <li><b>Logs</b> - Fired when a new block containing logs is received.
 *   <li><b>SynchronizerStatus </b> - Fired when the status of the synchronizer changes.
 * </ul>
 */
public interface BesuEvents extends BesuService {

  /**
   * Add a listener watching new blocks propagated.
   *
   * @param blockPropagatedListener The listener that will accept a BlockHeader as the event.
   * @return an id to be used as an identifier when de-registering the event.
   */
  long addBlockPropagatedListener(BlockPropagatedListener blockPropagatedListener);

  /**
   * Remove the blockAdded listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addBlockPropagatedListener
   */
  void removeBlockPropagatedListener(long listenerIdentifier);

  /**
   * Add a listener watching for new blocks added.
   *
   * @param blockAddedListener The listener that will accept the Block object as the event.
   * @return an id to be used as an identifier when de-registering the event.
   */
  long addBlockAddedListener(BlockAddedListener blockAddedListener);

  /**
   * Remove the block added listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addBlockAddedListener
   */
  void removeBlockAddedListener(long listenerIdentifier);

  /**
   * Add a listener watching for new reorg blocks added.
   *
   * @param blockReorgListener The listener that will accept the reorg Block object as the event.
   * @return an id to be used as an identifier when de-registering the event.
   */
  long addBlockReorgListener(BlockReorgListener blockReorgListener);

  /**
   * Remove the block reorg listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addBlockReorgListener
   */
  void removeBlockReorgListener(long listenerIdentifier);

  /**
   * Add a listener watching new transactions added to the node.
   *
   * @param transactionAddedListener The listener that will accept the Transaction object as the
   *     event.
   * @return an id to be used as an identifier when de-registering the event.
   */
  long addTransactionAddedListener(TransactionAddedListener transactionAddedListener);

  /**
   * Remove the blockAdded listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addTransactionAddedListener
   */
  void removeTransactionAddedListener(long listenerIdentifier);

  /**
   * Add a listener watching dropped transactions.
   *
   * @param transactionDroppedListener The listener that will accept the Transaction object as the
   *     event.
   * @return an id to be used as an identifier when de-registering the event.
   */
  long addTransactionDroppedListener(TransactionDroppedListener transactionDroppedListener);

  /**
   * Remove the transactionDropped listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addTransactionDroppedListener
   */
  void removeTransactionDroppedListener(long listenerIdentifier);

  /**
   * Add a listener watching the synchronizer status.
   *
   * @param syncStatusListener The listener that will accept the SyncStatus object as the event.
   * @return The id to be used as an identifier when de-registering the event.
   */
  long addSyncStatusListener(SyncStatusListener syncStatusListener);

  /**
   * Remove the sync status listener from besu notifications.
   *
   * @param listenerIdentifier The id that was returned from addSyncStatusListener
   */
  void removeSyncStatusListener(long listenerIdentifier);

  /**
   * Add a listener that consumes every log (both added and removed) that matches the filter
   * parameters when a new block is added to the blockchain.
   *
   * @param addresses The addresses from which the log filter will be created
   * @param topics The topics from which the log filter will be created
   * @param logListener The listener that will accept the log.
   * @return The id of the listener to be used to remove the listener.
   */
  long addLogListener(List<Address> addresses, List<List<Bytes32>> topics, LogListener logListener);

  /**
   * Remove the log listener with the associated id.
   *
   * @param listenerIdentifier The id of the listener that was returned from addLogListener
   */
  void removeLogListener(long listenerIdentifier);

  /** The listener interface for receiving new block propagated events. */
  interface BlockPropagatedListener {

    /**
     * Invoked when a new block header has been received and validated and is about to be sent out
     * to other peers, but before the body of the block has been evaluated and validated.
     *
     * <p>The block may not have been imported to the local chain yet and may fail later
     * validations.
     *
     * @param propagatedBlockContext block being propagated.
     */
    void onBlockPropagated(PropagatedBlockContext propagatedBlockContext);
  }

  /** The listener interface for receiving new block added events. */
  interface BlockAddedListener {

    /**
     * Invoked when a new block has been evaluated and validated.
     *
     * @param addedBlockContext block being added.
     */
    void onBlockAdded(AddedBlockContext addedBlockContext);
  }

  /** The listener interface for receiving new block reorg events. */
  interface BlockReorgListener {

    /**
     * Invoked when a reorg block has been evaluated and validated.
     *
     * @param addedBlockContext reorg block being added.
     */
    void onBlockReorg(AddedBlockContext addedBlockContext);
  }

  /** The listener interface for receiving new transaction added events. */
  interface TransactionAddedListener {

    /**
     * Invoked when a new transaction has been added to the node.
     *
     * @param transaction the new transaction.
     */
    void onTransactionAdded(Transaction transaction);
  }

  /** The listener interface for receiving transaction dropped events. */
  interface TransactionDroppedListener {

    /**
     * Invoked when a transaction is dropped from the node.
     *
     * @param transaction the dropped transaction.
     */
    void onTransactionDropped(Transaction transaction);
  }

  /** The listener interface for receiving sync status events. */
  interface SyncStatusListener {

    /**
     * Invoked when the synchronizer status changes
     *
     * @param syncStatus the sync status
     */
    void onSyncStatusChanged(Optional<SyncStatus> syncStatus);
  }

  /** The listener interface for receiving log events. */
  interface LogListener {

    /**
     * Invoked for each log (both added and removed) when a new block is added to the blockchain.
     *
     * @param logWithMetadata the log with associated metadata. see https://eth.wiki/json-rpc/API
     */
    void onLogEmitted(LogWithMetadata logWithMetadata);
  }

  interface TTDReachedListener {

    /**
     * Emitted when Total Terminal Difficulty is reached on a chain and dependent merge
     * functionality should trigger.
     *
     * @param reached is true when we reached TTD, can be potentially false in case we reorg under
     *     TTD
     */
    void onTTDReached(boolean reached);
  }

  interface InitialSyncCompletionListener {

    /** Emitted when initial sync finishes */
    void onInitialSyncCompleted();
  }
}
