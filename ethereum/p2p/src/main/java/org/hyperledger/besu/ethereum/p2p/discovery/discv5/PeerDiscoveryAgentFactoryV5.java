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
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgentFactory;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;

import java.util.Optional;

import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.MutableDiscoverySystem;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Factory for creating DiscV5 {@link PeerDiscoveryAgent} instances backed by the Ethereum Discovery
 * v5 {@link DiscoverySystemBuilder}.
 *
 * <p>This factory is responsible for:
 *
 * <ul>
 *   <li>Initializing the local {@link NodeRecord} via {@link NodeRecordManager}
 *   <li>Configuring and building a mutable DiscV5 discovery system
 *   <li>Wiring Besu-specific components such as fork ID handling and node key services
 * </ul>
 *
 * <p>The resulting {@link PeerDiscoveryAgent} integrates DiscV5 discovery with Besu’s P2P
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
   * @param nodeRecordManager manager responsible for local node record lifecycle
   * @param forkIdManager manager providing fork ID information for peer compatibility
   */
  public PeerDiscoveryAgentFactoryV5(
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final NodeRecordManager nodeRecordManager,
      final ForkIdManager forkIdManager) {
    this.config = config;
    this.nodeKey = nodeKey;
    this.forkIdManager = forkIdManager;
    this.nodeRecordManager = nodeRecordManager;
  }

  /**
   * Creates and configures a DiscV5 {@link PeerDiscoveryAgent}.
   *
   * <p>The provided {@link RlpxAgent} is ignored, as DiscV5 discovery operates independently of the
   * RLPx transport layer.
   *
   * @param rlpxAgent unused RLPx agent
   * @return a fully configured DiscV5 peer discovery agent
   * @throws IllegalStateException if the local node record has not been initialized
   */
  @Override
  public PeerDiscoveryAgent create(final RlpxAgent rlpxAgent) {

    final DiscoverySystemBuilder discoverySystemBuilder = new DiscoverySystemBuilder();
    nodeRecordManager.initializeLocalNode(
        config.getDiscovery().getAdvertisedHost(),
        config.getDiscovery().getBindPort(),
        config.getDiscovery().getBindPort());

    NodeRecord localNodeRecord =
        nodeRecordManager
            .getLocalNode()
            .flatMap(DiscoveryPeer::getNodeRecord)
            .orElseThrow(() -> new IllegalStateException("Local node record not initialized"));

    final MutableDiscoverySystem discoverySystem =
        discoverySystemBuilder
            .listen(config.getDiscovery().getBindHost(), config.getDiscovery().getBindPort())
            .signer(new LocalNodeKeySigner(nodeKey))
            .localNodeRecord(localNodeRecord)
            .localNodeRecordListener((previous, updated) -> nodeRecordManager.updateNodeRecord())
            .newAddressHandler((nodeRecord, newAddress) -> Optional.of(nodeRecord))
            // TODO Integrate address filtering based on peer permissions
            .addressAccessPolicy(AddressAccessPolicy.ALLOW_ALL)
            .buildMutable();

    return new PeerDiscoveryAgentV5(
        discoverySystem, config, forkIdManager, nodeRecordManager, rlpxAgent);
  }
}
