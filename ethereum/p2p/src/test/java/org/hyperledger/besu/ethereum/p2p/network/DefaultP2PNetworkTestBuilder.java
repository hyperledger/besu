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
package org.hyperledger.besu.ethereum.p2p.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.p2p.EthProtocolHelper;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DefaultRlpxAgentFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgentFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.RlpxAgentFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.discv4.PeerDiscoveryAgentFactoryDiscv4;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.vertx.core.Vertx;

public final class DefaultP2PNetworkTestBuilder {
  private DefaultP2PNetworkTestBuilder() {}

  static DefaultP2PNetwork.Builder builder(
      final NetworkingConfiguration config, final Vertx vertx, final NodeKey nodeKey) {
    return builder(
        config, vertx, nodeKey, PeerPermissions.noop(), List.of(EthProtocolHelper.LATEST));
  }

  static DefaultP2PNetwork.Builder builder(
      final NetworkingConfiguration config,
      final Vertx vertx,
      final NodeKey nodeKey,
      final PeerPermissions peerPermissions,
      final List<Capability> supportedCapabilities) {
    final MetricsSystem noopMetricsSystem = new NoOpMetricsSystem();

    final MutableBlockchain mockBlockchain = mock(MutableBlockchain.class);
    final Block mockGenesisBlock = mock(Block.class);
    when(mockGenesisBlock.getHash()).thenReturn(Hash.ZERO);
    when(mockBlockchain.getGenesisBlock()).thenReturn(mockGenesisBlock);

    final PeerDiscoveryAgentFactory discv4DiscoveryFactory =
        new PeerDiscoveryAgentFactoryDiscv4(
            vertx,
            nodeKey,
            config,
            peerPermissions,
            new NatService(Optional.empty()),
            noopMetricsSystem,
            new InMemoryKeyValueStorageProvider(),
            mockBlockchain,
            Collections.emptyList(),
            Collections.emptyList());

    final RlpxAgentFactory defaultRlpxFactory =
        new DefaultRlpxAgentFactory(
            nodeKey, config, peerPermissions, noopMetricsSystem, Stream::empty, Stream::empty);

    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .nodeKey(nodeKey)
        .config(config)
        .metricsSystem(noopMetricsSystem)
        .rlpxAgentFactory(defaultRlpxFactory)
        .discoveryAgentFactory(discv4DiscoveryFactory)
        .supportedCapabilities(supportedCapabilities);
  }
}
