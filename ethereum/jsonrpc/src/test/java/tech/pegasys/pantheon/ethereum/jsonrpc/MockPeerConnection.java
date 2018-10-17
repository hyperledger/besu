/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

public class MockPeerConnection implements PeerConnection {
  PeerInfo peerInfo;
  InetSocketAddress localAddress;
  InetSocketAddress remoteAddress;

  public MockPeerConnection(
      final PeerInfo peerInfo,
      final InetSocketAddress localAddress,
      final InetSocketAddress remoteAddress) {
    this.peerInfo = peerInfo;
    this.localAddress = localAddress;
    this.remoteAddress = remoteAddress;
  }

  @Override
  public void send(final Capability capability, final MessageData message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Capability> getAgreedCapabilities() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Capability capability(final String protocol) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PeerInfo getPeer() {
    return peerInfo;
  }

  @Override
  public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void disconnect(final DisconnectReason reason) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  @Override
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }
}
