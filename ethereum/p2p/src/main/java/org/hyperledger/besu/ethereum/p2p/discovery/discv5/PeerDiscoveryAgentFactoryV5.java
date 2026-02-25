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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgentFactory;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatService;

/**
 * Factory for creating DiscV5 {@link PeerDiscoveryAgent} instances.
 *
 * <p>This factory is responsible for wiring together the dependencies needed by {@link
 * PeerDiscoveryAgentV5}. It intentionally does <em>not</em> initialize the local node record or
 * build the {@link org.ethereum.beacon.discovery.MutableDiscoverySystem} — both are deferred to
 * {@link PeerDiscoveryAgentV5#start(int)}, where the actual RLPx TCP port is known.
 *
 * <p>The resulting {@link PeerDiscoveryAgent} integrates DiscV5 discovery with Besu's P2P
 * networking stack.
 */
public final class PeerDiscoveryAgentFactoryV5 implements PeerDiscoveryAgentFactory {
  private final NetworkingConfiguration config;

  private final NodeRecordManager nodeRecordManager;
  private final NodeKey nodeKey;
  private final ForkIdManager forkIdManager;

  /**
   * Creates a new DiscV5 peer discovery agent factory.
   *
   * @param nodeKey the local node key used for identity and signing
   * @param config the networking configuration
   * @param natService NAT service for external address discovery
   * @param storageProvider storage provider for persisting node records
   * @param forkIdManager manager providing fork ID information for peer compatibility
   */
  public PeerDiscoveryAgentFactoryV5(
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final NatService natService,
      final StorageProvider storageProvider,
      final ForkIdManager forkIdManager) {
    this.config = config;
    this.nodeKey = nodeKey;
    this.forkIdManager = forkIdManager;
    this.nodeRecordManager =
        new NodeRecordManager(storageProvider, nodeKey, forkIdManager, natService);
  }

  /**
   * Creates a DiscV5 {@link PeerDiscoveryAgent}.
   *
   * <p>The local node record and discovery system are built lazily during {@link
   * PeerDiscoveryAgentV5#start(int)} so that the ENR {@code tcp}/{@code tcp6} fields receive the
   * actual RLPx TCP port rather than the discovery bind port.
   *
   * @param rlpxAgent the RLPx agent
   * @return a configured DiscV5 peer discovery agent ready to be started
   */
  @Override
  public PeerDiscoveryAgent create(final RlpxAgent rlpxAgent) {
    return new PeerDiscoveryAgentV5(
        nodeKey,
        config,
        forkIdManager,
        nodeRecordManager,
        rlpxAgent,
        config.discoveryConfiguration().isPreferIpv6Outbound());
  }
}
