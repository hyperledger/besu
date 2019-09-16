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
package org.hyperledger.besu.ethereum.p2p.discovery;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

/**
 * Represents an Ethereum node that we interacting with through the discovery and wire protocols.
 */
public class DiscoveryPeer extends DefaultPeer {
  private PeerDiscoveryStatus status = PeerDiscoveryStatus.KNOWN;
  // Endpoint is a datastructure used in discovery messages
  private final Endpoint endpoint;

  // Timestamps.
  private long firstDiscovered = 0;
  private long lastContacted = 0;
  private long lastSeen = 0;
  private long lastAttemptedConnection = 0;

  private DiscoveryPeer(final EnodeURL enode, final Endpoint endpoint) {
    super(enode);
    this.endpoint = endpoint;
  }

  public static DiscoveryPeer fromEnode(final EnodeURL enode) {
    return new DiscoveryPeer(enode, Endpoint.fromEnode(enode));
  }

  public static DiscoveryPeer fromIdAndEndpoint(final BytesValue id, final Endpoint endpoint) {
    return new DiscoveryPeer(endpoint.toEnode(id), endpoint);
  }

  public static DiscoveryPeer readFrom(final RLPInput in) {
    final int size = in.enterList();

    // The last list item will be the id, pass size - 1 to Endpoint
    final Endpoint endpoint = Endpoint.decodeInline(in, size - 1);
    final BytesValue id = in.readBytesValue();
    in.leaveList();

    return DiscoveryPeer.fromIdAndEndpoint(id, endpoint);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    endpoint.encodeInline(out);
    out.writeBytesValue(getId());
    out.endList();
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

  public long getLastAttemptedConnection() {
    return lastAttemptedConnection;
  }

  public void setLastAttemptedConnection(final long lastAttemptedConnection) {
    this.lastAttemptedConnection = lastAttemptedConnection;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(final long lastSeen) {
    this.lastSeen = lastSeen;
  }

  public Endpoint getEndpoint() {
    return endpoint;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DiscoveryPeer{");
    sb.append("status=").append(status);
    sb.append(", enode=").append(this.getEnodeURL());
    sb.append(", firstDiscovered=").append(firstDiscovered);
    sb.append(", lastContacted=").append(lastContacted);
    sb.append(", lastSeen=").append(lastSeen);
    sb.append('}');
    return sb.toString();
  }
}
