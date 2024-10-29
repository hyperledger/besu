/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.ImmutableCheckpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class CheckPointSyncChainDownloaderTest {

  protected ProtocolSchedule protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  private PeerTaskExecutor peerTaskExecutor;
  protected ProtocolContext protocolContext;
  private SyncState syncState;

  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private Checkpoint checkpoint;

  private WorldStateStorageCoordinator worldStateStorageCoordinator;

  static class CheckPointSyncChainDownloaderTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setup(final DataStorageFormat dataStorageFormat) {
    final WorldStateKeyValueStorage worldStateKeyValueStorage;
    if (dataStorageFormat.equals(DataStorageFormat.BONSAI)) {
      worldStateKeyValueStorage = mock(BonsaiWorldStateKeyValueStorage.class);
      when(((BonsaiWorldStateKeyValueStorage) worldStateKeyValueStorage)
              .isWorldStateAvailable(any(), any()))
          .thenReturn(true);
    } else {
      worldStateKeyValueStorage = mock(ForestWorldStateKeyValueStorage.class);
      when(((ForestWorldStateKeyValueStorage) worldStateKeyValueStorage)
              .isWorldStateAvailable(any()))
          .thenReturn(true);
    }
    when(worldStateKeyValueStorage.getDataStorageFormat()).thenReturn(dataStorageFormat);
    worldStateStorageCoordinator = new WorldStateStorageCoordinator(worldStateKeyValueStorage);

    final BlockchainSetupUtil localBlockchainSetup =
        BlockchainSetupUtil.forTesting(dataStorageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forTesting(dataStorageFormat);
    otherBlockchain = otherBlockchainSetup.getBlockchain();
    otherBlockchainSetup.importFirstBlocks(30);
    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            protocolSchedule,
            localBlockchain,
            new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()));
    ethContext = ethProtocolManager.ethContext();

    final int blockNumber = 10;
    checkpoint =
        ImmutableCheckpoint.builder()
            .blockNumber(blockNumber)
            .blockHash(localBlockchainSetup.getBlocks().get(blockNumber).getHash())
            .totalDifficulty(Difficulty.ONE)
            .build();

    syncState =
        new SyncState(
            protocolContext.getBlockchain(),
            ethContext.getEthPeers(),
            true,
            Optional.of(checkpoint));

    peerTaskExecutor = mock(PeerTaskExecutor.class);

    when(peerTaskExecutor.execute(any(GetReceiptsFromPeerTask.class)))
        .thenAnswer(
            new Answer<PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>>>() {
              @Override
              public PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> answer(
                  final InvocationOnMock invocationOnMock) throws Throwable {
                GetReceiptsFromPeerTask task =
                    invocationOnMock.getArgument(0, GetReceiptsFromPeerTask.class);

                return processTask(task);
              }
            });
  }

  @SuppressWarnings("unchecked")
  private PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> processTask(
      final GetReceiptsFromPeerTask task) throws IllegalAccessException {
    Map<BlockHeader, List<TransactionReceipt>> getReceiptsFromPeerTaskResult = new HashMap<>();
    List<Field> fields =
        ReflectionUtils.findFields(
            task.getClass(),
            (field) -> field.getName().equals("blockHeaders"),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);
    fields.forEach((f) -> f.setAccessible(true));
    Collection<BlockHeader> blockHeaders = (Collection<BlockHeader>) fields.getFirst().get(task);
    blockHeaders.forEach(
        (bh) ->
            getReceiptsFromPeerTaskResult.put(
                bh, otherBlockchain.getTxReceipts(bh.getHash()).get()));

    return new PeerTaskExecutorResult<>(
        Optional.of(getReceiptsFromPeerTaskResult), PeerTaskExecutorResponseCode.SUCCESS);
  }

  @AfterEach
  void tearDown() {
    if (ethContext != null) {
      ethProtocolManager.stop();
    }
  }

  private ChainDownloader downloader(
      final SynchronizerConfiguration syncConfig, final long pivotBlockNumber) {
    return CheckpointSyncChainDownloader.create(
        syncConfig,
        worldStateStorageCoordinator,
        protocolSchedule,
        protocolContext,
        ethContext,
        peerTaskExecutor,
        syncState,
        new NoOpMetricsSystem(),
        new FastSyncState(otherBlockchain.getBlockHeader(pivotBlockNumber).get()),
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  @ParameterizedTest
  @ArgumentsSource(CheckPointSyncChainDownloaderTestArguments.class)
  public void shouldSyncToPivotBlockInMultipleSegments(final DataStorageFormat storageFormat)
      throws IllegalAccessException {
    setup(storageFormat);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .isPeerTaskSystemEnabled(false)
            .build();
    final long pivotBlockNumber = 25;
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            ethPeer -> {
              ethPeer.setCheckpointHeader(
                  otherBlockchainSetup.getBlocks().get((int) checkpoint.blockNumber()).getHeader());
            });
    final ChainDownloader downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @ParameterizedTest
  @ArgumentsSource(CheckPointSyncChainDownloaderTestArguments.class)
  public void shouldSyncToPivotBlockInSingleSegment(final DataStorageFormat storageFormat)
      throws IllegalAccessException {
    setup(storageFormat);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final long pivotBlockNumber = 10;
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build();
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            ethPeer -> {
              ethPeer.setCheckpointHeader(
                  otherBlockchainSetup.getBlocks().get((int) checkpoint.blockNumber()).getHeader());
            });
    final ChainDownloader downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @ParameterizedTest
  @ArgumentsSource(CheckPointSyncChainDownloaderTestArguments.class)
  public void shouldSyncToPivotBlockInMultipleSegmentsWithPeerTaskSystem(
      final DataStorageFormat storageFormat)
      throws IllegalAccessException, ExecutionException, InterruptedException, TimeoutException {
    setup(storageFormat);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .isPeerTaskSystemEnabled(true)
            .build();
    final long pivotBlockNumber = 25;
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            ethPeer -> {
              ethPeer.setCheckpointHeader(
                  otherBlockchainSetup.getBlocks().get((int) checkpoint.blockNumber()).getHeader());
            });
    final ChainDownloader downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @ParameterizedTest
  @ArgumentsSource(CheckPointSyncChainDownloaderTestArguments.class)
  public void shouldSyncToPivotBlockInSingleSegmentWithPeerTaskSystem(
      final DataStorageFormat storageFormat) throws IllegalAccessException {
    setup(storageFormat);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final long pivotBlockNumber = 10;
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(true).build();
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            ethPeer -> {
              ethPeer.setCheckpointHeader(
                  otherBlockchainSetup.getBlocks().get((int) checkpoint.blockNumber()).getHeader());
            });
    final ChainDownloader downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
