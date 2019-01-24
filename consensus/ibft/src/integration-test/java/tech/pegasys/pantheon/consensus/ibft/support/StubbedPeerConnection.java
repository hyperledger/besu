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
package tech.pegasys.pantheon.consensus.ibft.support;

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.SocketAddress;
import java.util.Set;

public class StubbedPeerConnection implements PeerConnection {
  private final BytesValue nodeId;

  public StubbedPeerConnection(final BytesValue nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public void send(final Capability capability, final MessageData message)
      throws PeerNotConnected {}

  @Override
  public Set<Capability> getAgreedCapabilities() {
    return null;
  }

  @Override
  public PeerInfo getPeer() {
    return new PeerInfo(0, "IbftIntTestPeer", emptyList(), 0, nodeId);
  }

  @Override
  public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {}

  @Override
  public void disconnect(final DisconnectReason reason) {}

  @Override
  public SocketAddress getLocalAddress() {
    return null;
  }

  @Override
  public SocketAddress getRemoteAddress() {
    return null;
  }
}
