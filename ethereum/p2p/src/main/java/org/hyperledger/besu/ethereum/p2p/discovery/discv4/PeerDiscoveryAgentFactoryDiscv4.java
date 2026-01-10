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
package org.hyperledger.besu.ethereum.p2p.discovery.discv4;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgentFactory;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;

import io.vertx.core.Vertx;

public class PeerDiscoveryAgentFactoryDiscv4 implements PeerDiscoveryAgentFactory {

  private final Vertx vertx;
  private final NodeKey nodeKey;
  private final NetworkingConfiguration config;
  private final PeerPermissions peerPermissions;
  private final NatService natService;
  private final MetricsSystem metricsSystem;
  private final StorageProvider storageProvider;
  private final Blockchain blockchain;
  private final List<Long> blockNumberForks;
  private final List<Long> timestampForks;

  public PeerDiscoveryAgentFactoryDiscv4(
      final Vertx vertx,
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final PeerPermissions peerPermissions,
      final NatService natService,
      final MetricsSystem metricsSystem,
      final StorageProvider storageProvider,
      final Blockchain blockchain,
      final List<Long> blockNumberForks,
      final List<Long> timestampForks) {

    this.vertx = vertx;
    this.nodeKey = nodeKey;
    this.config = config;
    this.peerPermissions = peerPermissions;
    this.natService = natService;
    this.metricsSystem = metricsSystem;
    this.storageProvider = storageProvider;
    this.blockchain = blockchain;
    this.blockNumberForks = blockNumberForks;
    this.timestampForks = timestampForks;
  }

  @Override
  public PeerDiscoveryAgent create(final RlpxAgent rlpxAgent) {
    final ForkIdManager forkIdManager =
        new ForkIdManager(blockchain, blockNumberForks, timestampForks);

    return VertxPeerDiscoveryAgent.create(
        vertx,
        nodeKey,
        config.getDiscovery(),
        peerPermissions,
        natService,
        metricsSystem,
        storageProvider,
        forkIdManager,
        rlpxAgent);
  }
}
