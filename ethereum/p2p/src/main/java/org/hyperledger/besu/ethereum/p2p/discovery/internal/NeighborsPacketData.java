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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;

public class NeighborsPacketData implements PacketData {

  private final List<DiscoveryPeer> peers;

  /* In seconds after epoch. */
  private final long expiration;

  private NeighborsPacketData(final List<DiscoveryPeer> peers, final long expiration) {
    checkArgument(peers != null, "peer list cannot be null");
    checkArgument(expiration >= 0, "expiration must be positive");

    this.peers = peers;
    this.expiration = expiration;
  }

  public static NeighborsPacketData create(final List<DiscoveryPeer> peers) {
    return new NeighborsPacketData(peers, PacketData.defaultExpiration());
  }

  public static NeighborsPacketData readFrom(final RLPInput in) {
    in.enterList();
    final List<DiscoveryPeer> peers = in.readList(DiscoveryPeer::readFrom);
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new NeighborsPacketData(peers, expiration);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeList(peers, DiscoveryPeer::writeTo);
    out.writeLongScalar(expiration);
    out.endList();
  }

  public List<DiscoveryPeer> getNodes() {
    return peers;
  }

  public long getExpiration() {
    return expiration;
  }

  @Override
  public String toString() {
    return String.format("NeighborsPacketData{peers=%s, expiration=%d}", peers, expiration);
  }
}
