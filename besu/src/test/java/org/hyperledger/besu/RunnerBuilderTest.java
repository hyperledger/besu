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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.BLOCKCHAIN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.network.PeerConnectionTracker;
import org.hyperledger.besu.consensus.common.bft.protocol.BftProtocolManager;
import org.hyperledger.besu.consensus.ibft.protocol.IbftSubProtocol;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.KeyPairSecurityModule;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class RunnerBuilderTest {

  @Rule public TemporaryFolder dataDir = new TemporaryFolder();

  @Mock BesuController besuController;
  @Mock Vertx vertx;

  @Before
  public void setup() {
    final SubProtocolConfiguration subProtocolConfiguration = mock(SubProtocolConfiguration.class);
    final EthProtocolManager ethProtocolManager = mock(EthProtocolManager.class);
    final EthContext ethContext = mock(EthContext.class);
    final ProtocolContext protocolContext = mock(ProtocolContext.class);
    final NodeKey nodeKey =
        new NodeKey(new KeyPairSecurityModule(new SECP256K1().generateKeyPair()));

    when(subProtocolConfiguration.getProtocolManagers())
        .thenReturn(
            Collections.singletonList(
                new BftProtocolManager(
                    mock(BftEventQueue.class),
                    mock(PeerConnectionTracker.class),
                    IbftSubProtocol.IBFV1,
                    IbftSubProtocol.get().getName())));
    when(ethContext.getScheduler()).thenReturn(mock(EthScheduler.class));
    when(ethProtocolManager.ethContext()).thenReturn(ethContext);
    when(subProtocolConfiguration.getSubProtocols())
        .thenReturn(Collections.singletonList(new IbftSubProtocol()));
    when(protocolContext.getBlockchain()).thenReturn(mock(DefaultBlockchain.class));

    when(besuController.getProtocolManager()).thenReturn(ethProtocolManager);
    when(besuController.getSubProtocolConfiguration()).thenReturn(subProtocolConfiguration);
    when(besuController.getProtocolContext()).thenReturn(protocolContext);
    when(besuController.getProtocolSchedule()).thenReturn(mock(ProtocolSchedule.class));
    when(besuController.getNodeKey()).thenReturn(nodeKey);
    when(besuController.getMiningParameters()).thenReturn(mock(MiningParameters.class));
    when(besuController.getPrivacyParameters()).thenReturn(mock(PrivacyParameters.class));
    when(besuController.getTransactionPool()).thenReturn(mock(TransactionPool.class));
    when(besuController.getSynchronizer()).thenReturn(mock(Synchronizer.class));
    when(besuController.getMiningCoordinator()).thenReturn(mock(MiningCoordinator.class));
  }

  @Test
  public void enodeUrlShouldHaveAdvertisedHostWhenDiscoveryDisabled() {
    final String p2pAdvertisedHost = "172.0.0.1";
    final int p2pListenPort = 30302;

    final Runner runner =
        new RunnerBuilder()
            .p2pListenInterface("0.0.0.0")
            .p2pListenPort(p2pListenPort)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pEnabled(true)
            .discovery(false)
            .besuController(besuController)
            .ethNetworkConfig(mock(EthNetworkConfig.class))
            .metricsSystem(mock(ObservableMetricsSystem.class))
            .jsonRpcConfiguration(mock(JsonRpcConfiguration.class))
            .permissioningService(mock(PermissioningServiceImpl.class))
            .graphQLConfiguration(mock(GraphQLConfiguration.class))
            .webSocketConfiguration(mock(WebSocketConfiguration.class))
            .metricsConfiguration(mock(MetricsConfiguration.class))
            .vertx(vertx)
            .dataDir(dataDir.getRoot().toPath())
            .storageProvider(mock(KeyValueStorageProvider.class))
            .forkIdSupplier(() -> Collections.singletonList(Bytes.EMPTY))
            .rpcEndpointService(new RpcEndpointServiceImpl())
            .build();
    runner.startEthereumMainLoop();

    final EnodeURL expectedEodeURL =
        EnodeURLImpl.builder()
            .ipAddress(p2pAdvertisedHost)
            .discoveryPort(0)
            .listeningPort(p2pListenPort)
            .nodeId(besuController.getNodeKey().getPublicKey().getEncoded())
            .build();
    assertThat(runner.getLocalEnode().orElseThrow()).isEqualTo(expectedEodeURL);
  }

  @Test
  public void movingAcrossProtocolSpecsUpdatesNodeRecord() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final String p2pAdvertisedHost = "172.0.0.1";
    final int p2pListenPort = 30301;
    final StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
    final Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        createInMemoryBlockchain(genesisBlock, new MainnetBlockHeaderFunctions());
    when(besuController.getProtocolContext().getBlockchain()).thenReturn(blockchain);
    final Runner runner =
        new RunnerBuilder()
            .discovery(true)
            .p2pListenInterface("0.0.0.0")
            .p2pListenPort(p2pListenPort)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pEnabled(true)
            .natMethod(NatMethod.NONE)
            .besuController(besuController)
            .ethNetworkConfig(mock(EthNetworkConfig.class))
            .metricsSystem(mock(ObservableMetricsSystem.class))
            .permissioningService(mock(PermissioningServiceImpl.class))
            .jsonRpcConfiguration(mock(JsonRpcConfiguration.class))
            .graphQLConfiguration(mock(GraphQLConfiguration.class))
            .webSocketConfiguration(mock(WebSocketConfiguration.class))
            .metricsConfiguration(mock(MetricsConfiguration.class))
            .vertx(Vertx.vertx())
            .dataDir(dataDir.getRoot().toPath())
            .storageProvider(storageProvider)
            .forkIdSupplier(() -> Collections.singletonList(Bytes.EMPTY))
            .rpcEndpointService(new RpcEndpointServiceImpl())
            .build();
    runner.startEthereumMainLoop();
    when(besuController.getProtocolSchedule().streamMilestoneBlocks())
        .thenAnswer(__ -> Stream.of(1L, 2L));
    for (int i = 0; i < 2; ++i) {
      final Block block =
          gen.block(
              BlockDataGenerator.BlockOptions.create()
                  .setBlockNumber(1 + i)
                  .setParentHash(blockchain.getChainHeadHash()));
      blockchain.appendBlock(block, gen.receipts(block));
      assertThat(
              storageProvider
                  .getStorageBySegmentIdentifier(BLOCKCHAIN)
                  .get("local-enr-seqno".getBytes(StandardCharsets.UTF_8))
                  .map(Bytes::of)
                  .map(NodeRecordFactory.DEFAULT::fromBytes)
                  .map(NodeRecord::getSeq))
          .contains(UInt64.valueOf(2 + i));
    }
  }
}
