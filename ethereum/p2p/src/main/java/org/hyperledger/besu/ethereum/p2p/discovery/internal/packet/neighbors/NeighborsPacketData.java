/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;

import java.util.List;

public class NeighborsPacketData implements PacketData {

  private final List<DiscoveryPeer> peers;

  /* In seconds after epoch. */
  private final long expiration;

  NeighborsPacketData(final List<DiscoveryPeer> peers, final long expiration) {
    this.peers = peers;
    this.expiration = expiration;
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
