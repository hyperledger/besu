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
import tech.pegasys.pantheon.plugin.data.Transaction;

/**
 * This service allows plugins to attach to various events during the normal operation of Pantheon.
 *
 * <p>Currently supported events
 *
 * <ul>
 *   <li><b>newBlockPropagated</b> - Fired when a new block header has been received and validated
 *       and is about to be sent out to other peers, but before the body of the block has been
 *       evaluated and validated.
 *   <li><b>newTransactionAdded</b> - Fired when a new transaction has been added to the node.
 * </ul>
 */
@Unstable
public interface PantheonEvents {

  /**
   * Add a listener watching new blocks propagated.
   *
   * @param newBlockPropagatedListener The listener that will accept a BlockHeader as the event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  Object addNewBlockPropagatedListener(NewBlockPropagatedListener newBlockPropagatedListener);

  /**
   * Remove the blockAdded listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addBlockAddedListener;
   */
  void removeNewBlockPropagatedListener(Object listenerIdentifier);

  /**
   * Add a listener watching new transactions added to the node.
   *
   * @param newTransactionAddedListener The listener that will accept the Transaction object as the
   *     event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  Object addNewTransactionAddedListener(NewTransactionAddedListener newTransactionAddedListener);

  /**
   * Remove the blockAdded listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addNewTransactionAddedListener;
   */
  void removeNewTransactionAddedListener(Object listenerIdentifier);

  /**
   * Add a listener watching dropped transactions.
   *
   * @param newTransactionDroppedListener The listener that will accept the Transaction object as
   *     the event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  Object addNewTransactionDroppedListener(TransactionDroppedListener newTransactionDroppedListener);

  /**
   * Remove the transactionDropped listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addTransactionDroppedListener;
   */
  void removeTransactionDroppedListener(Object listenerIdentifier);

  /** The listener interface for receiving new block propagated events. */
  interface NewBlockPropagatedListener {

    /**
     * Invoked when a new block header has been received and validated and is about to be sent out
     * to other peers, but before the body of the block has been evaluated and validated.
     *
     * <p>The block may not have been imported to the local chain yet and may fail later
     * validations.
     *
     * @param newBlockHeader the new block header.
     */
    void newBlockPropagated(BlockHeader newBlockHeader);
  }

  /** The listener interface for receiving new transaction added events. */
  interface NewTransactionAddedListener {

    /**
     * Invoked when a new transaction has been added to the node.
     *
     * @param transaction the new transaction.
     */
    void newTransactionAdded(Transaction transaction);
  }

  /** The listener interface for receiving transaction dropped events. */
  interface TransactionDroppedListener {

    /**
     * Invoked when a transaction is dropped from the node.
     *
     * @param transaction the dropped transaction.
     */
    void newTransactionDropped(Transaction transaction);
  }
}
