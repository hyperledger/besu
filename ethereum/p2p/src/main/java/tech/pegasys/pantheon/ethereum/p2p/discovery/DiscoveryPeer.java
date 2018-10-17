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
package tech.pegasys.pantheon.ethereum.p2p.discovery;

import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerId;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.OptionalInt;

/**
 * Represents an Ethereum node that we interacting with through the discovery and wire protocols.
 */
public class DiscoveryPeer extends DefaultPeer {
  private PeerDiscoveryStatus status = PeerDiscoveryStatus.KNOWN;

  // Timestamps.
  private long firstDiscovered = 0;
  private long lastContacted = 0;
  private long lastSeen = 0;

  public DiscoveryPeer(
      final BytesValue id, final String host, final int udpPort, final int tcpPort) {
    super(id, host, udpPort, tcpPort);
  }

  public DiscoveryPeer(
      final BytesValue id, final String host, final int udpPort, final OptionalInt tcpPort) {
    super(id, host, udpPort, tcpPort);
  }

  public DiscoveryPeer(final BytesValue id, final String host, final int udpPort) {
    super(id, host, udpPort);
  }

  public DiscoveryPeer(final BytesValue id, final Endpoint endpoint) {
    super(id, endpoint);
  }

  public DiscoveryPeer(final Peer peer) {
    super(peer.getId(), peer.getEndpoint());
  }

  public PeerDiscoveryStatus getStatus() {
    return status;
  }

  public void setStatus(final PeerDiscoveryStatus status) {
    this.status = status;
  }

  public long getFirstDiscovered() {
    return firstDiscovered;
  }

  public PeerId setFirstDiscovered(final long firstDiscovered) {
    this.firstDiscovered = firstDiscovered;
    return this;
  }

  public long getLastContacted() {
    return lastContacted;
  }

  public void setLastContacted(final long lastContacted) {
    this.lastContacted = lastContacted;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(final long lastSeen) {
    this.lastSeen = lastSeen;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DiscoveryPeer{");
    sb.append("status=").append(status);
    sb.append(", endPoint=").append(this.getEndpoint());
    sb.append(", firstDiscovered=").append(firstDiscovered);
    sb.append(", lastContacted=").append(lastContacted);
    sb.append(", lastSeen=").append(lastSeen);
    sb.append('}');
    return sb.toString();
  }
}
