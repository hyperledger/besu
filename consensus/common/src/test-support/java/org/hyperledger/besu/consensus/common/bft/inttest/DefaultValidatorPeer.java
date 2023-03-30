/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.common.bft.inttest;

import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.events.BftEvents;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;

public abstract class DefaultValidatorPeer {

  protected final Address nodeAddress;
  protected final NodeKey nodeKey;
  private final PeerConnection peerConnection;
  protected final List<MessageData> receivedMessages = Lists.newArrayList();

  private final EventMultiplexer localEventMultiplexer;
  private long estimatedChainHeight = 0;

  protected DefaultValidatorPeer(
      final NodeParams nodeParams, final EventMultiplexer localEventMultiplexer) {
    this.nodeKey = nodeParams.getNodeKey();
    this.nodeAddress = nodeParams.getAddress();
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    this.peerConnection = StubbedPeerConnection.create(nodeId);
    this.localEventMultiplexer = localEventMultiplexer;
  }

  public Address getNodeAddress() {
    return nodeAddress;
  }

  public NodeKey getnodeKey() {
    return nodeKey;
  }

  public PeerConnection getPeerConnection() {
    return peerConnection;
  }

  public SECPSignature getBlockSignature(final Hash digest) {
    return nodeKey.sign(digest);
  }

  public void handleReceivedMessage(final MessageData message) {
    receivedMessages.add(message);
  }

  public List<MessageData> getReceivedMessages() {
    return Collections.unmodifiableList(receivedMessages);
  }

  public void clearReceivedMessages() {
    receivedMessages.clear();
  }

  public void injectMessage(final MessageData msgData) {
    final DefaultMessage message = new DefaultMessage(peerConnection, msgData);
    localEventMultiplexer.handleBftEvent(BftEvents.fromMessage(message));
  }

  public void updateEstimatedChainHeight(final long estimatedChainHeight) {
    this.estimatedChainHeight = estimatedChainHeight;
  }

  public void verifyEstimatedChainHeightEquals(final long expectedChainHeight) {
    Assertions.assertThat(estimatedChainHeight).isEqualTo(expectedChainHeight);
  }
}
