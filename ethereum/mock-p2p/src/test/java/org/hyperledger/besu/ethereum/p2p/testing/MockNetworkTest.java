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
package org.hyperledger.besu.ethereum.p2p.testing;

import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests for {@link MockNetwork}. */
public final class MockNetworkTest {

  @Test
  public void exchangeMessages() throws Exception {
    final Capability cap = Capability.create("eth", 63);
    final MockNetwork network = new MockNetwork(Arrays.asList(cap));
    final Peer one =
        DefaultPeer.fromEnodeURL(
            EnodeURLImpl.builder()
                .nodeId(randomId())
                .ipAddress("192.168.1.2")
                .discoveryPort(1234)
                .listeningPort(4321)
                .build());
    final Peer two =
        DefaultPeer.fromEnodeURL(
            EnodeURLImpl.builder()
                .nodeId(randomId())
                .ipAddress("192.168.1.3")
                .discoveryPort(1234)
                .listeningPort(4321)
                .build());
    try (final P2PNetwork network1 = network.setup(one);
        final P2PNetwork network2 = network.setup(two)) {
      final CompletableFuture<Message> messageFuture = new CompletableFuture<>();
      network1.subscribe(cap, (capability, msg) -> messageFuture.complete(msg));
      final Predicate<PeerConnection> isPeerOne =
          peerConnection -> peerConnection.getPeerInfo().getNodeId().equals(one.getId());
      final Predicate<PeerConnection> isPeerTwo =
          peerConnection -> peerConnection.getPeerInfo().getNodeId().equals(two.getId());
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst())
          .isNotPresent();
      Assertions.assertThat(network2.getPeers().stream().filter(isPeerOne).findFirst())
          .isNotPresent();

      // Validate Connect Behaviour
      final CompletableFuture<PeerConnection> peer2Future = new CompletableFuture<>();
      network1.subscribeConnect(peer2Future::complete);
      final CompletableFuture<PeerConnection> peer1Future = new CompletableFuture<>();
      network2.subscribeConnect(peer1Future::complete);
      network1.connect(two).get();
      Assertions.assertThat(peer1Future.get().getPeerInfo().getNodeId()).isEqualTo(one.getId());
      Assertions.assertThat(peer2Future.get().getPeerInfo().getNodeId()).isEqualTo(two.getId());
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst()).isPresent();
      final Optional<PeerConnection> optionalConnection =
          network2.getPeers().stream().filter(isPeerOne).findFirst();
      Assertions.assertThat(optionalConnection).isPresent();

      // Validate Message Exchange
      final int size = 128;
      final byte[] data = new byte[size];
      ThreadLocalRandom.current().nextBytes(data);
      final int code = 0x74;
      final PeerConnection connection = optionalConnection.get();
      connection.send(cap, new RawMessage(code, Bytes.wrap(data)));
      final Message receivedMessage = messageFuture.get();
      final MessageData receivedMessageData = receivedMessage.getData();
      Assertions.assertThat(receivedMessageData.getData()).isEqualTo(Bytes.wrap(data));
      Assertions.assertThat(receivedMessage.getConnection().getPeerInfo().getNodeId())
          .isEqualTo(two.getId());
      Assertions.assertThat(receivedMessageData.getSize()).isEqualTo(size);
      Assertions.assertThat(receivedMessageData.getCode()).isEqualTo(code);

      // Validate Disconnect Behaviour
      final CompletableFuture<DisconnectReason> peer1DisconnectFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> peer2DisconnectFuture = new CompletableFuture<>();
      network2.subscribeDisconnect(
          (peer, reason, initiatedByPeer) -> peer1DisconnectFuture.complete(reason));
      network1.subscribeDisconnect(
          (peer, reason, initiatedByPeer) -> peer2DisconnectFuture.complete(reason));
      connection.disconnect(DisconnectReason.CLIENT_QUITTING);
      Assertions.assertThat(peer1DisconnectFuture.get()).isEqualTo(DisconnectReason.REQUESTED);
      Assertions.assertThat(peer2DisconnectFuture.get())
          .isEqualTo(DisconnectReason.CLIENT_QUITTING);
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst())
          .isNotPresent();
      Assertions.assertThat(network2.getPeers().stream().filter(isPeerOne).findFirst())
          .isNotPresent();
    }
  }

  private static Bytes randomId() {
    return Peer.randomId();
  }
}
