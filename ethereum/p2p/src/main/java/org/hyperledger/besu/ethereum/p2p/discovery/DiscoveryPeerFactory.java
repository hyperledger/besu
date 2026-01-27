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

public class DiscoveryPeerFactory {

  private DiscoveryPeerFactory() {
    // utility class
  }

  public static DiscoveryPeer fromEnode(final EnodeURL enode) {
    return DiscoveryPeerV4.fromEnode(enode);
  }

  public static DiscoveryPeer fromEthereumNodeRecord(final EthereumNodeRecord enr) {
    DiscoveryPeer peer = fromEnode(buildEnodeUrl(enr));
    peer.setNodeRecord(enr.nodeRecord());
    return peer;
  }

  private static EnodeURL buildEnodeUrl(final EthereumNodeRecord enr) {
    return EnodeURLImpl.builder()
        .ipAddress(enr.ip())
        .nodeId(enr.publicKey())
        .discoveryPort(enr.udp())
        .listeningPort(enr.tcp())
        .build();
  }
}
