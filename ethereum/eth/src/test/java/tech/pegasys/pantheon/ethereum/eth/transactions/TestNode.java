/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static org.assertj.core.util.Preconditions.checkNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.network.DefaultP2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.network.NetworkRunner;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestNode implements Closeable {

  private static final Logger LOG = LogManager.getLogger();
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  protected final SECP256K1.KeyPair kp;
  protected final P2PNetwork network;
  protected final Peer selfPeer;
  protected final Map<PeerConnection, DisconnectReason> disconnections = new HashMap<>();
  private final TransactionPool transactionPool;

  public TestNode(
      final Vertx vertx,
      final Integer port,
      final SECP256K1.KeyPair kp,
      final DiscoveryConfiguration discoveryCfg) {
    checkNotNull(vertx);
    checkNotNull(discoveryCfg);

    final int listenPort = port != null ? port : 0;
    this.kp = kp != null ? kp : SECP256K1.KeyPair.generate();

    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(discoveryCfg)
            .setRlpx(
                RlpxConfiguration.create()
                    .setBindPort(listenPort)
                    .setSupportedProtocols(EthProtocol.get()));

    final GenesisConfigFile genesisConfigFile = GenesisConfigFile.development();
    final ProtocolSchedule<Void> protocolSchedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfigFile.development().getConfigOptions(), false);

    final GenesisState genesisState = GenesisState.fromConfig(genesisConfigFile, protocolSchedule);
    final BlockHeaderFunctions blockHeaderFunctions =
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
    final MutableBlockchain blockchain =
        createInMemoryBlockchain(genesisState.getBlock(), blockHeaderFunctions);
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    genesisState.writeStateTo(worldStateArchive.getMutable());
    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(blockchain, worldStateArchive, null);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            worldStateArchive,
            BigInteger.ONE,
            false,
            1,
            1,
            1,
            TestClock.fixed(),
            new NoOpMetricsSystem(),
            EthProtocolConfiguration.defaultConfig());

    final NetworkRunner networkRunner =
        NetworkRunner.builder()
            .subProtocols(EthProtocol.get())
            .protocolManagers(singletonList(ethProtocolManager))
            .network(
                capabilities ->
                    DefaultP2PNetwork.builder()
                        .vertx(vertx)
                        .keyPair(this.kp)
                        .config(networkingConfiguration)
                        .metricsSystem(new NoOpMetricsSystem())
                        .supportedCapabilities(capabilities)
                        .build())
            .metricsSystem(new NoOpMetricsSystem())
            .build();
    network = networkRunner.getNetwork();
    network.subscribeDisconnect(
        (connection, reason, initiatedByPeer) -> disconnections.put(connection, reason));

    final EthContext ethContext = ethProtocolManager.ethContext();

    final SyncState syncState = mock(SyncState.class);
    when(syncState.isInSync(anyLong())).thenReturn(true);

    transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethContext,
            TestClock.fixed(),
            metricsSystem,
            syncState,
            Wei.ZERO,
            TransactionPoolConfiguration.builder().build());

    networkRunner.start();
    selfPeer = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());
  }

  public BytesValue id() {
    return kp.getPublicKey().getEncodedBytes();
  }

  public static String shortId(final BytesValue id) {
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
