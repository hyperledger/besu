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
package org.hyperledger.besu.consensus.qbft.network;

import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.qbft.core.types.QbftGossiper;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

/** Class responsible for rebroadcasting QBFT messages to known validators */
public class QbftGossip implements Gossiper, QbftGossiper {

  private final ValidatorMulticaster multicaster;

  /**
   * Constructor that attaches gossip logic to a set of multicaster
   *
   * @param multicaster Network connections to the remote validators
   */
  public QbftGossip(final ValidatorMulticaster multicaster) {
    this.multicaster = multicaster;
  }

  /**
   * Retransmit a given QBFT message to other known validators nodes
   *
   * @param message The raw message to be gossiped
   */
  @Override
  public void send(final Message message) {
    final MessageData messageData = message.getData();
    gossipMessage(messageData);
  }

  /**
   * Retransmit a given QBFT message event to other known validators nodes
   *
   * @param messageEvent The BFT message event to be gossiped
   */
  public void send(final BftReceivedMessageEvent messageEvent) {
    final MessageData messageData = messageEvent.getMessage().getData();
    gossipMessage(messageData);
  }

  /**
   * Send a QBFT message to other validators (from QbftGossiper interface).
   *
   * @param message the QBFT message to send
   */
  @Override
  public void send(final QbftMessage message) {
    final MessageData messageData = message.getMessageData();
    gossipMessageWithoutSender(messageData);
  }

  /**
   * Send a QBFT message to other validators with replay flag (from QbftGossiper interface).
   *
   * @param message the QBFT message to send
   * @param isReplay true if this message is being replayed from a future message buffer
   */
  @Override
  public void send(final QbftMessage message, final boolean isReplay) {
    // For now, we ignore the replay flag - can be enhanced later
    send(message);
  }

  /**
   * Internal method to handle the gossip logic for QBFT messages
   *
   * @param messageData The message data to be gossiped
   */
  private void gossipMessage(final MessageData messageData) {
    // Send to all validators - denylist logic removed as per architectural decision
    // Note: This may result in messages being sent back to sender/author
    multicaster.send(messageData);
  }

  /**
   * Internal method to handle the gossip logic for QBFT messages without sender information
   *
   * @param messageData The message data to be gossiped
   */
  private void gossipMessageWithoutSender(final MessageData messageData) {
    // Send to all validators - denylist logic removed as per architectural decision
    // Note: This may result in messages being sent back to author
    multicaster.send(messageData);
  }
}
