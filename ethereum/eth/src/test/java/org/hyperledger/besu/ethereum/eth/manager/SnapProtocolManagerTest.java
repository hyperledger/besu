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
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection.PeerSendHandler;
import org.hyperledger.besu.ethereum.eth.messages.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public final class SnapProtocolManagerTest {

  public static SnapProtocolManager create() {
    EthPeers peers = new EthPeers(EthProtocol.NAME, TestClock.fixed(), new NoOpMetricsSystem());
    EthMessages snapMessages = new EthMessages();
    return new SnapProtocolManager(Collections.emptyList(), peers, snapMessages);
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final SnapProtocolManager snapManager, final PeerSendHandler onSend) {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(SnapProtocol.SNAP1));
    final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
    snapManager.handleNewConnection(peer);
    return peer;
  }

  @Test
  public void disconnectOnUnsolicitedMessage() {
    try (final SnapProtocolManager snapManager = create()) {
      final MessageData messageData = AccountRangeMessage.create().wrapMessageData(BigInteger.ONE);
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(snapManager, (cap, msg, conn) -> {});
      snapManager.processMessage(SnapProtocol.SNAP1, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void respondToGetAccountRange() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SnapProtocolManager snapManager = create()) {
      final Hash worldStateRoot = Hash.hash(Bytes.wrap(new byte[] {0x01}));
      final Hash startKeyHash = Hash.hash(Bytes.wrap(new byte[] {0x02}));
      final Hash endKeyHash = Hash.hash(Bytes.wrap(new byte[] {0x03}));
      final MessageData messageData =
          GetAccountRangeMessage.create(worldStateRoot, startKeyHash, endKeyHash);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            assertThat(message.getCode()).isEqualTo(SnapV1.ACCOUNT_RANGE);
            done.complete(null);
          };
      final PeerConnection peer = setupPeerWithoutStatusExchange(snapManager, onSend);
      snapManager.processMessage(SnapProtocol.SNAP1, new DefaultMessage(peer, messageData));
      done.get();
    }
  }
}
