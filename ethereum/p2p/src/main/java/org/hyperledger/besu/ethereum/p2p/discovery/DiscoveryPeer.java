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
package org.hyperledger.besu.ethereum.p2p.discovery;

import org.hyperledger.besu.ethereum.forkid.ForkId;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Represents an Ethereum node that we are interacting with through the discovery and wire
 * protocols.
 */
public class DiscoveryPeer extends DefaultPeer {
  private PeerDiscoveryStatus status = PeerDiscoveryStatus.KNOWN;
  // Endpoint is a datastructure used in discovery messages
  private final Endpoint endpoint;

  // Timestamps.
  private final AtomicLong firstDiscovered = new AtomicLong(0L);
  private long lastAttemptedConnection = 0;

  private NodeRecord nodeRecord;
  private Optional<ForkId> forkId = Optional.empty();

  private DiscoveryPeer(final EnodeURL enode, final Endpoint endpoint) {
    super(enode);
    this.endpoint = endpoint;
  }

  public static DiscoveryPeer fromEnode(final EnodeURL enode) {
    return new DiscoveryPeer(enode, Endpoint.fromEnode(enode));
  }

  public static Optional<DiscoveryPeer> from(final Peer peer) {
    if (peer instanceof DiscoveryPeer) {
      return Optional.of((DiscoveryPeer) peer);
    }

    return Optional.of(peer)
        .map(Peer::getEnodeURL)
        .filter(EnodeURL::isRunningDiscovery)
        .map(DiscoveryPeer::fromEnode);
  }

  public static DiscoveryPeer fromIdAndEndpoint(final Bytes id, final Endpoint endpoint) {
    return new DiscoveryPeer(endpoint.toEnode(id), endpoint);
  }

  public static DiscoveryPeer readFrom(final RLPInput in) {
    final int size = in.enterList();

    // The last list item will be the id, pass size - 1 to Endpoint
    final Endpoint endpoint = Endpoint.decodeInline(in, size - 1);
    final Bytes id = in.readBytes();
    in.leaveList();

    return DiscoveryPeer.fromIdAndEndpoint(id, endpoint);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    endpoint.encodeInline(out);
    out.writeBytes(getId());
    out.endList();
  }

  public PeerDiscoveryStatus getStatus() {
    return status;
  }

  public void setStatus(final PeerDiscoveryStatus status) {
    this.status = status;
  }

  public long getFirstDiscovered() {
    return firstDiscovered.get();
  }

  public void setFirstDiscovered(final long firstDiscovered) {
    this.firstDiscovered.compareAndExchange(0L, firstDiscovered);
  }

  public long getLastAttemptedConnection() {
    return lastAttemptedConnection;
  }

  public void setLastAttemptedConnection(final long lastAttemptedConnection) {
    this.lastAttemptedConnection = lastAttemptedConnection;
  }

  public Endpoint getEndpoint() {
    return endpoint;
  }

  @Override
  public Optional<NodeRecord> getNodeRecord() {
    return Optional.ofNullable(nodeRecord);
  }

  public void setNodeRecord(final NodeRecord nodeRecord) {
    this.nodeRecord = nodeRecord;
    this.forkId = ForkId.fromRawForkId(nodeRecord.get("eth"));
  }

  @Override
  public Optional<ForkId> getForkId() {
    return this.forkId;
  }

  @Override
  public void setForkId(final ForkId forkId) {
    this.forkId = Optional.ofNullable(forkId);
  }

  public boolean discoveryEndpointMatches(final DiscoveryPeer peer) {
    return peer.getEndpoint().getHost().equals(endpoint.getHost())
        && peer.getEndpoint().getUdpPort() == endpoint.getUdpPort();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DiscoveryPeer{");
    sb.append("status=").append(status);
    sb.append(", enode=").append(this.getEnodeURL());
    sb.append(", firstDiscovered=").append(firstDiscovered);
    sb.append('}');
    return sb.toString();
  }
}
