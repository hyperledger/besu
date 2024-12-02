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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_IS_HIGH_SPEC;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastWorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.NodeDataRequest;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Thread)
public class WorldStateDownloaderBenchmark {

  private final BlockDataGenerator dataGen = new BlockDataGenerator();
  private Path tempDir;
  private BlockHeader blockHeader;
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private WorldStateDownloader worldStateDownloader;
  private WorldStateStorageCoordinator worldStateStorageCoordinator;
  private RespondingEthPeer peer;
  private Responder responder;
  private InMemoryTasksPriorityQueues<NodeDataRequest> pendingRequests;
  private StorageProvider storageProvider;
  private EthProtocolManager ethProtocolManager;

  @Setup(Level.Invocation)
  public void setUpUnchangedState() {
    final SynchronizerConfiguration syncConfig =
        new SynchronizerConfiguration.Builder().worldStateHashCountPerRequest(200).build();
    final Hash stateRoot = createExistingWorldState();
    blockHeader = new BlockHeaderTestFixture().stateRoot(stateRoot).buildHeader();

    tempDir = Files.createTempDir().toPath();
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setEthScheduler(
                new EthScheduler(
                    syncConfig.getDownloaderParallelism(),
                    syncConfig.getTransactionsParallelism(),
                    syncConfig.getComputationParallelism(),
                    metricsSystem))
            .build();

    peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockHeader.getNumber());

    final EthContext ethContext = ethProtocolManager.ethContext();

    final StorageProvider storageProvider =
        createKeyValueStorageProvider(tempDir, tempDir.resolve("database"));
    worldStateStorageCoordinator =
        storageProvider.createWorldStateStorageCoordinator(DataStorageConfiguration.DEFAULT_CONFIG);

    pendingRequests = new InMemoryTasksPriorityQueues<>();
    worldStateDownloader =
        new FastWorldStateDownloader(
            ethContext,
            worldStateStorageCoordinator,
            pendingRequests,
            syncConfig.getWorldStateHashCountPerRequest(),
            syncConfig.getWorldStateRequestParallelism(),
            syncConfig.getWorldStateMaxRequestsWithoutProgress(),
            syncConfig.getWorldStateMinMillisBeforeStalling(),
            Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
            metricsSystem,
            SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  private Hash createExistingWorldState() {
    // Setup existing state
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    final MutableWorldState worldState = worldStateArchive.getMutable();

    dataGen.createRandomAccounts(worldState, 10000);

    responder = RespondingEthPeer.blockchainResponder(null, worldStateArchive);
    return worldState.rootHash();
  }

  @TearDown(Level.Invocation)
  public void tearDown() throws Exception {
    ethProtocolManager.stop();
    ethProtocolManager.awaitStop();
    pendingRequests.close();
    storageProvider.close();
    MoreFiles.deleteRecursively(tempDir, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Benchmark
  public Optional<Bytes> downloadWorldState() {
    final CompletableFuture<Void> result =
        worldStateDownloader.run(null, new FastSyncState(blockHeader));
    if (result.isDone()) {
      throw new IllegalStateException("World state download was already complete");
    }
    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());
    result.getNow(null);
    final Optional<Bytes> rootData =
        worldStateStorageCoordinator
            .getStrategy(ForestWorldStateKeyValueStorage.class)
            .getNodeData(blockHeader.getStateRoot());
    if (rootData.isEmpty()) {
      throw new IllegalStateException("World state download did not complete.");
    }
    return rootData;
  }

  private StorageProvider createKeyValueStorageProvider(final Path dataDir, final Path dbDir) {
    final var besuConfiguration = new BesuConfigurationImpl();
    besuConfiguration.init(dataDir, dbDir, DataStorageConfiguration.DEFAULT_CONFIG);
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () ->
                    new RocksDBFactoryConfiguration(
                        DEFAULT_MAX_OPEN_FILES,
                        DEFAULT_BACKGROUND_THREAD_COUNT,
                        DEFAULT_CACHE_CAPACITY,
                        DEFAULT_IS_HIGH_SPEC),
                Arrays.asList(KeyValueSegmentIdentifier.values()),
                RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
        .withCommonConfiguration(besuConfiguration)
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}
