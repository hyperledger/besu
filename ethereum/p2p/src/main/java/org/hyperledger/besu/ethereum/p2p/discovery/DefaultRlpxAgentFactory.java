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

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.PeerPrivileges;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerLookup;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class DefaultRlpxAgentFactory implements RlpxAgentFactory {

  private final NodeKey nodeKey;
  private final NetworkingConfiguration config;
  private final PeerPermissions peerPermissions;
  private final MetricsSystem metricsSystem;
  private final Supplier<Stream<PeerConnection>> allConnectionsSupplier;
  private final Supplier<Stream<PeerConnection>> allActiveConnectionsSupplier;

  public DefaultRlpxAgentFactory(
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final PeerPermissions peerPermissions,
      final MetricsSystem metricsSystem,
      final Supplier<Stream<PeerConnection>> allConnectionsSupplier,
      final Supplier<Stream<PeerConnection>> allActiveConnectionsSupplier) {

    this.nodeKey = nodeKey;
    this.config = config;
    this.peerPermissions = peerPermissions;
    this.metricsSystem = metricsSystem;
    this.allConnectionsSupplier = allConnectionsSupplier;
    this.allActiveConnectionsSupplier = allActiveConnectionsSupplier;
  }

  @Override
  public RlpxAgent create(
      final LocalNode localNode, final PeerPrivileges peerPrivileges, final PeerLookup peerLookup) {

    return RlpxAgent.builder()
        .nodeKey(nodeKey)
        .config(config.getRlpx())
        .peerPermissions(peerPermissions)
        .peerPrivileges(peerPrivileges)
        .localNode(localNode)
        .metricsSystem(metricsSystem)
        .allConnectionsSupplier(allConnectionsSupplier)
        .allActiveConnectionsSupplier(allActiveConnectionsSupplier)
        .peerLookup(peerLookup)
        .build();
  }
}
