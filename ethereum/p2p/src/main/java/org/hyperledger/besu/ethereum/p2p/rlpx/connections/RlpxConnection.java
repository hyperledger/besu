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

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;

public abstract class RlpxConnection {

  private final long initiatedAt;
  protected final CompletableFuture<PeerConnection> future;

  private RlpxConnection(final CompletableFuture<PeerConnection> future) {
    this.future = future;
    this.initiatedAt = System.currentTimeMillis();
  }

  public static RlpxConnection inboundConnection(final PeerConnection peerConnection) {
    return new RemotelyInitiatedRlpxConnection(peerConnection);
  }

  public static RlpxConnection outboundConnection(
      final Peer peer, final CompletableFuture<PeerConnection> future) {
    return new LocallyInitiatedRlpxConnection(peer, future);
  }

  public abstract Peer getPeer();

  public abstract void disconnect(DisconnectReason reason);

  public Bytes getId() {
    return getPeer().getId();
  }

  public abstract PeerConnection getPeerConnection() throws ConnectionNotEstablishedException;

  public CompletableFuture<PeerConnection> getFuture() {
    return future;
  }

  public abstract boolean isActive();

  public abstract boolean isPending();

  public abstract boolean isFailedOrDisconnected();

  public abstract boolean initiatedRemotely();

  public void subscribeConnectionEstablished(
      final RlpxConnectCallback successCallback, final RlpxConnectFailedCallback failedCallback) {
    future.whenComplete(
        (conn, err) -> {
          if (err != null) {
            failedCallback.onFailure(this);
          } else {
            successCallback.onConnect(this);
          }
        });
  }

  public boolean initiatedLocally() {
    return !initiatedRemotely();
  }

  public long getInitiatedAt() {
    return initiatedAt;
  }

  private static class RemotelyInitiatedRlpxConnection extends RlpxConnection {

    private final PeerConnection peerConnection;

    private RemotelyInitiatedRlpxConnection(final PeerConnection peerConnection) {
      super(CompletableFuture.completedFuture(peerConnection));
      this.peerConnection = peerConnection;
    }

    @Override
    public Peer getPeer() {
      return peerConnection.getPeer();
    }

    @Override
    public void disconnect(final DisconnectReason reason) {
      peerConnection.disconnect(reason);
    }

    @Override
    public PeerConnection getPeerConnection() {
      return peerConnection;
    }

    @Override
    public boolean isActive() {
      return !peerConnection.isDisconnected();
    }

    @Override
    public boolean isPending() {
      return false;
    }

    @Override
    public boolean isFailedOrDisconnected() {
      return peerConnection.isDisconnected();
    }

    @Override
    public boolean initiatedRemotely() {
      return true;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof RemotelyInitiatedRlpxConnection)) {
        return false;
      }
      final RemotelyInitiatedRlpxConnection that = (RemotelyInitiatedRlpxConnection) o;
      return Objects.equals(peerConnection, that.peerConnection);
    }

    @Override
    public int hashCode() {
      return Objects.hash(peerConnection);
    }

    @Override
    public String toString() {
      return "RemotelyInitiatedRlpxConnection initiatedAt:"
          + getInitiatedAt()
          + " to "
          + peerConnection.getPeer().getId()
          + " disconnected? "
          + isFailedOrDisconnected();
    }
  }

  private static class LocallyInitiatedRlpxConnection extends RlpxConnection {

    private final Peer peer;

    private LocallyInitiatedRlpxConnection(
        final Peer peer, final CompletableFuture<PeerConnection> future) {
      super(future);
      this.peer = peer;
    }

    @Override
    public Peer getPeer() {
      return peer;
    }

    @Override
    public void disconnect(final DisconnectReason reason) {
      future.thenAccept((conn) -> conn.disconnect(reason));
    }

    @Override
    public PeerConnection getPeerConnection() throws ConnectionNotEstablishedException {
      if (!future.isDone() || future.isCompletedExceptionally()) {
        throw new ConnectionNotEstablishedException(
            "Cannot access PeerConnection before connection is fully established.");
      }
      return future.getNow(null);
    }

    @Override
    public boolean isActive() {
      return future.isDone()
          && !future.isCompletedExceptionally()
          && !getPeerConnection().isDisconnected();
    }

    @Override
    public boolean isPending() {
      return !future.isDone();
    }

    @Override
    public boolean isFailedOrDisconnected() {
      return future.isCompletedExceptionally()
          || (future.isDone() && getPeerConnection().isDisconnected());
    }

    @Override
    public boolean initiatedRemotely() {
      return false;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof LocallyInitiatedRlpxConnection)) {
        return false;
      }
      final LocallyInitiatedRlpxConnection that = (LocallyInitiatedRlpxConnection) o;
      return Objects.equals(peer, that.peer) && Objects.equals(future, that.future);
    }

    @Override
    public int hashCode() {
      return Objects.hash(peer, future);
    }

    @Override
    public String toString() {
      return "LocallyInitiatedRlpxConnection initiatedAt:"
          + getInitiatedAt()
          + " to "
          + getPeer().getId()
          + " disconnected? "
          + isFailedOrDisconnected();
    }
  }

  public static class ConnectionNotEstablishedException extends IllegalStateException {

    public ConnectionNotEstablishedException(final String message) {
      super(message);
    }
  }

  @FunctionalInterface
  public interface RlpxConnectCallback {
    void onConnect(RlpxConnection connection);
  }

  @FunctionalInterface
  public interface RlpxConnectFailedCallback {
    void onFailure(RlpxConnection connection);
  }
}
