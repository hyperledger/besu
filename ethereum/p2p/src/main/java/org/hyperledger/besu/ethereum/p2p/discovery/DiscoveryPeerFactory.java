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
package org.hyperledger.besu.ethereum.p2p.discovery;

import org.hyperledger.besu.ethereum.p2p.discovery.discv4.internal.DiscoveryPeerV4;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.EthereumNodeRecord;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.plugin.data.EnodeURL;

import org.ethereum.beacon.discovery.schema.NodeRecord;

public class DiscoveryPeerFactory {

  private DiscoveryPeerFactory() {
    // utility class
  }

  public static DiscoveryPeer fromEnode(final EnodeURL enode) {
    return DiscoveryPeerV4.fromEnode(enode);
  }

  public static DiscoveryPeer fromNodeRecord(final NodeRecord nodeRecord) {
    return fromNodeRecord(nodeRecord, false);
  }

  public static DiscoveryPeer fromNodeRecord(
      final NodeRecord nodeRecord, final boolean preferIpv6) {
    EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(nodeRecord);
    return fromEthereumNodeRecord(enr, preferIpv6);
  }

  public static DiscoveryPeer fromEthereumNodeRecord(final EthereumNodeRecord enr) {
    return fromEthereumNodeRecord(enr, false);
  }

  public static DiscoveryPeer fromEthereumNodeRecord(
      final EthereumNodeRecord enr, final boolean preferIpv6) {
    DiscoveryPeer peer = fromEnode(buildEnodeUrl(enr, preferIpv6));
    peer.setNodeRecord(enr.nodeRecord());
    return peer;
  }

  private static EnodeURL buildEnodeUrl(final EthereumNodeRecord enr, final boolean preferIpv6) {
    if (preferIpv6 && enr.ipv6().isPresent()) {
      return EnodeURLImpl.builder()
          .ipAddress(enr.ipv6().get())
          .nodeId(enr.publicKey())
          .discoveryPort(enr.udpV6())
          .listeningPort(enr.tcpV6())
          .build();
    }
    return EnodeURLImpl.builder()
        .ipAddress(enr.ip())
        .nodeId(enr.publicKey())
        .discoveryPort(enr.udp())
        .listeningPort(enr.tcp())
        .build();
  }
}
