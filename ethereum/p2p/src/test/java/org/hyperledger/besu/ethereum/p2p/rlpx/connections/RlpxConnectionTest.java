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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper.createPeer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.RlpxConnection.ConnectionNotEstablishedException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.concurrent.CompletableFuture;

import org.junit.Test;

public class RlpxConnectionTest {

  @Test
  public void getPeer_pendingOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);

    assertThat(conn.getPeer()).isEqualTo(peer);
  }

  @Test
  public void getPeer_establishedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    future.complete(peerConnection(peer));

    assertThat(conn.getPeer()).isEqualTo(peer);
  }

  @Test
  public void getPeer_inboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);

    assertThat(conn.getPeer()).isEqualTo(peer);
  }

  @Test
  public void disconnect_pendingOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);

    final DisconnectReason reason = DisconnectReason.REQUESTED;
    conn.disconnect(reason);
    assertThat(conn.isFailedOrDisconnected()).isFalse();

    // Resolve future
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);

    // Check disconnect was issued
    verify(peerConnection).disconnect(reason);
    assertThat(conn.isFailedOrDisconnected()).isTrue();
  }

  @Test
  public void disconnect_activeOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);

    final DisconnectReason reason = DisconnectReason.REQUESTED;
    conn.disconnect(reason);

    // Check disconnect was issued
    assertThat(conn.isFailedOrDisconnected()).isTrue();
    verify(peerConnection).disconnect(reason);
  }

  @Test
  public void disconnect_failedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    future.completeExceptionally(new IllegalStateException("whoops"));

    assertThat(conn.isFailedOrDisconnected()).isTrue();
    final DisconnectReason reason = DisconnectReason.REQUESTED;
    conn.disconnect(reason);
    assertThat(conn.isFailedOrDisconnected()).isTrue();
  }

  @Test
  public void disconnect_inboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);

    assertThat(conn.isFailedOrDisconnected()).isFalse();
    final DisconnectReason reason = DisconnectReason.REQUESTED;
    conn.disconnect(reason);

    // Check disconnect was issued
    assertThat(conn.isFailedOrDisconnected()).isTrue();
    verify(peerConnection).disconnect(reason);
  }

  @Test
  public void getPeerConnection_pendingOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);

    assertThatThrownBy(conn::getPeerConnection)
        .isInstanceOf(ConnectionNotEstablishedException.class);
  }

  @Test
  public void getPeerConnection_activeOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);

    assertThat(conn.getPeerConnection()).isEqualTo(peerConnection);
  }

  @Test
  public void getPeerConnection_failedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    future.completeExceptionally(new IllegalStateException("whoops"));

    assertThatThrownBy(conn::getPeerConnection)
        .isInstanceOf(ConnectionNotEstablishedException.class);
  }

  @Test
  public void getPeerConnection_disconnectedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);
    conn.disconnect(DisconnectReason.REQUESTED);

    assertThat(conn.getPeerConnection()).isEqualTo(peerConnection);
  }

  @Test
  public void getPeerConnection_activeInboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);

    assertThat(conn.getPeerConnection()).isEqualTo(peerConnection);
  }

  @Test
  public void getPeerConnection_disconnectedInboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);
    conn.disconnect(DisconnectReason.REQUESTED);

    assertThat(conn.getPeerConnection()).isEqualTo(peerConnection);
  }

  @Test
  public void checkState_pendingOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);

    assertThat(conn.initiatedRemotely()).isFalse();
    assertThat(conn.isActive()).isFalse();
    assertThat(conn.isPending()).isTrue();
    assertThat(conn.isFailedOrDisconnected()).isFalse();
  }

  @Test
  public void checkState_activeOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);

    assertThat(conn.initiatedRemotely()).isFalse();
    assertThat(conn.isActive()).isTrue();
    assertThat(conn.isPending()).isFalse();
    assertThat(conn.isFailedOrDisconnected()).isFalse();
  }

  @Test
  public void checkState_failedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    future.completeExceptionally(new IllegalStateException("whoops"));

    assertThat(conn.initiatedRemotely()).isFalse();
    assertThat(conn.isActive()).isFalse();
    assertThat(conn.isPending()).isFalse();
    assertThat(conn.isFailedOrDisconnected()).isTrue();
  }

  @Test
  public void checkState_disconnectedOutboundConnection() {
    final Peer peer = createPeer();
    final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
    final RlpxConnection conn = RlpxConnection.outboundConnection(peer, future);
    final PeerConnection peerConnection = peerConnection(peer);
    future.complete(peerConnection);
    conn.disconnect(DisconnectReason.UNKNOWN);

    assertThat(conn.initiatedRemotely()).isFalse();
    assertThat(conn.isActive()).isFalse();
    assertThat(conn.isPending()).isFalse();
    assertThat(conn.isFailedOrDisconnected()).isTrue();
  }

  @Test
  public void checkState_activeInboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);

    assertThat(conn.initiatedRemotely()).isTrue();
    assertThat(conn.isActive()).isTrue();
    assertThat(conn.isPending()).isFalse();
    assertThat(conn.isFailedOrDisconnected()).isFalse();
  }

  @Test
  public void checkState_disconnectedInboundConnection() {
    final Peer peer = createPeer();
    final PeerConnection peerConnection = peerConnection(peer);
    final RlpxConnection conn = RlpxConnection.inboundConnection(peerConnection);
    conn.disconnect(DisconnectReason.UNKNOWN);

    assertThat(conn.initiatedRemotely()).isTrue();
    assertThat(conn.isActive()).isFalse();
    assertThat(conn.isPending()).isFalse();
    assertThat(conn.isFailedOrDisconnected()).isTrue();
  }

  private PeerConnection peerConnection(final Peer peer) {
    return spy(MockPeerConnection.create(peer));
  }
}
