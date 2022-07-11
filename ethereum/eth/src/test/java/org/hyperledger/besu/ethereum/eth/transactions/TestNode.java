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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static org.assertj.core.util.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.DefaultP2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestNode implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(TestNode.class);
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  protected final NodeKey nodeKey;
  protected final P2PNetwork network;
  protected final Peer selfPeer;
  protected final Map<PeerConnection, DisconnectReason> disconnections = new HashMap<>();
  private final TransactionPool transactionPool;

  public TestNode(
      final Vertx vertx,
      final Integer port,
      final KeyPair kp,
      final DiscoveryConfiguration discoveryCfg) {
    checkNotNull(vertx);
    checkNotNull(discoveryCfg);

    final int listenPort = port != null ? port : 0;
    this.nodeKey = kp != null ? NodeKeyUtils.createFrom(kp) : NodeKeyUtils.generate();

    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(discoveryCfg)
            .setRlpx(
                RlpxConfiguration.create()
                    .setBindPort(listenPort)
                    .setSupportedProtocols(EthProtocol.get()));

    final GenesisConfigFile genesisConfigFile = GenesisConfigFile.development();
    final ProtocolSchedule protocolSchedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfigFile.development().getConfigOptions(), false, EvmConfiguration.DEFAULT);

    final GenesisState genesisState = GenesisState.fromConfig(genesisConfigFile, protocolSchedule);
    final BlockHeaderFunctions blockHeaderFunctions =
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
    final MutableBlockchain blockchain =
        createInMemoryBlockchain(genesisState.getBlock(), blockHeaderFunctions);
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    genesisState.writeStateTo(worldStateArchive.getMutable());
    final ProtocolContext protocolContext =
        new ProtocolContext(blockchain, worldStateArchive, null);

    final SyncState syncState = mock(SyncState.class);
    when(syncState.isInSync(anyLong())).thenReturn(true);

    final EthMessages ethMessages = new EthMessages();

    final EthPeers ethPeers =
        new EthPeers(
            EthProtocol.NAME,
            TestClock.fixed(),
            metricsSystem,
            25,
            EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);

    final EthScheduler scheduler = new EthScheduler(1, 1, 1, metricsSystem);
    final EthContext ethContext = new EthContext(ethPeers, ethMessages, scheduler);

    transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethContext,
            TestClock.fixed(),
            metricsSystem,
            syncState::isInitialSyncPhaseDone,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            TransactionPoolConfiguration.DEFAULT);

    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            BigInteger.ONE,
            worldStateArchive,
            transactionPool,
            EthProtocolConfiguration.defaultConfig(),
            ethPeers,
            ethMessages,
            ethContext,
            Collections.emptyList(),
            Optional.empty(),
            false,
            scheduler);

    final NetworkRunner networkRunner =
        NetworkRunner.builder()
            .subProtocols(EthProtocol.get())
            .protocolManagers(singletonList(ethProtocolManager))
            .network(
                capabilities ->
                    DefaultP2PNetwork.builder()
                        .vertx(vertx)
                        .nodeKey(nodeKey)
                        .config(networkingConfiguration)
                        .metricsSystem(new NoOpMetricsSystem())
                        .supportedCapabilities(capabilities)
                        .storageProvider(new InMemoryKeyValueStorageProvider())
                        .forkIdSupplier(() -> Collections.singletonList(Bytes.EMPTY))
                        .build())
            .metricsSystem(new NoOpMetricsSystem())
            .build();
    network = networkRunner.getNetwork();
    network.subscribeDisconnect(
        (connection, reason, initiatedByPeer) -> disconnections.put(connection, reason));

    networkRunner.start();
    selfPeer = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());
  }

  public Bytes id() {
    return nodeKey.getPublicKey().getEncodedBytes();
  }

  public static String shortId(final Bytes id) {
    return id.slice(62).toString().substring(2);
  }

  public String shortId() {
    return shortId(id());
  }

  public Peer selfPeer() {
    return selfPeer;
  }

  public CompletableFuture<PeerConnection> connect(final TestNode remoteNode) {
    return network.connect(remoteNode.selfPeer());
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void close() throws IOException {
    IOException firstEx = null;
    try {
      network.close();
    } catch (final IOException e) {
      if (firstEx == null) {
        firstEx = e;
      }
      LOG.warn("Error closing.  Continuing", e);
    }

    if (firstEx != null) {
      throw new IOException("Unable to close successfully.  Wrapping first exception.", firstEx);
    }
  }

  @Override
  public String toString() {
    return shortId()
        + "@"
        + selfPeer.getEnodeURL().getIpAsString()
        + ':'
        + selfPeer.getEnodeURL().getListeningPortOrZero();
  }

  public void receiveRemoteTransaction(final Transaction transaction) {
    transactionPool.addRemoteTransactions(singletonList(transaction));
  }

  public void receiveLocalTransaction(final Transaction transaction) {
    transactionPool.addLocalTransaction(transaction);
  }

  public int getPendingTransactionCount() {
    return transactionPool.getPendingTransactions().size();
  }
}
