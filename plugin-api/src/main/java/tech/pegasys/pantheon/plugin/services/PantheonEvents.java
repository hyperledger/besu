/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin.services;

import tech.pegasys.pantheon.plugin.Unstable;
import tech.pegasys.pantheon.plugin.data.BlockHeader;
import tech.pegasys.pantheon.plugin.data.SyncStatus;
import tech.pegasys.pantheon.plugin.data.Transaction;

/**
 * This service allows plugins to attach to various events during the normal operation of Pantheon.
 *
 * <p>Currently supported events
 *
 * <ul>
 *   <li><b>BlockPropagated</b> - Fired when a new block header has been received and validated and
 *       is about to be sent out to other peers, but before the body of the block has been evaluated
 *       and validated.
 *   <li><b>TransactionAdded</b> - Fired when a new transaction has been added to the node.
 *   <li><b>TransactionDropped</b> - Fired when a new transaction has been dropped from the node.
 *   <li><b>Logs</b> - Fired when a new block containing logs is received.
 *   <li><b>SynchronizerStatus </b> - Fired when the status of the synchronizer changes.
 * </ul>
 */
@Unstable
public interface PantheonEvents {

  /**
   * Add a listener watching new blocks propagated.
   *
   * @param blockPropagatedListener The listener that will accept a BlockHeader as the event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  long addBlockPropagatedListener(BlockPropagatedListener blockPropagatedListener);

  /**
   * Remove the blockAdded listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addBlockAddedListener;
   */
  void removeBlockPropagatedListener(long listenerIdentifier);

  /**
   * Add a listener watching new transactions added to the node.
   *
   * @param transactionAddedListener The listener that will accept the Transaction object as the
   *     event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  long addTransactionAddedListener(TransactionAddedListener transactionAddedListener);

  /**
   * Remove the blockAdded listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addTransactionAddedListener;
   */
  void removeTransactionAddedListener(long listenerIdentifier);

  /**
   * Add a listener watching dropped transactions.
   *
   * @param transactionDroppedListener The listener that will accept the Transaction object as the
   *     event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  long addTransactionDroppedListener(TransactionDroppedListener transactionDroppedListener);

  /**
   * Remove the transactionDropped listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addTransactionDroppedListener;
   */
  void removeTransactionDroppedListener(long listenerIdentifier);

  /**
   * Add a listener watching the synchronizer status.
   *
   * @param syncStatusListener The listener that will accept the SyncStatus object as the event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  long addSyncStatusListener(SyncStatusListener syncStatusListener);

  /**
   * Remove the logs listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addTransactionDroppedListener;
   */
  void removeSyncStatusListener(long listenerIdentifier);

  /** The listener interface for receiving new block propagated events. */
  interface BlockPropagatedListener {

    /**
     * Invoked when a new block header has been received and validated and is about to be sent out
     * to other peers, but before the body of the block has been evaluated and validated.
     *
     * <p>The block may not have been imported to the local chain yet and may fail later
     * validations.
     *
     * @param blockHeader the new block header.
     */
    void newBlockPropagated(BlockHeader blockHeader);
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
    void onSyncStatusChanged(SyncStatus syncStatus);
  }
}
