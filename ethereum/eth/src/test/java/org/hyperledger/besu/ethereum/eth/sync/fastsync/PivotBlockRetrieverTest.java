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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class PivotBlockRetrieverTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;
  private TransactionPool transactionPool;
  private PivotBlockRetriever pivotBlockRetriever;
  private ProtocolSchedule protocolSchedule;
  private PeerTaskExecutor peerTaskExecutor;

  static class PivotBlockRetrieverTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setEthScheduler(new DeterministicEthScheduler(timeout::get))
            .setWorldStateArchive(blockchainSetupUtil.getWorldArchive())
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();

    pivotBlockRetriever = createPivotBlockRetriever(3, 1, 1);
  }

  private PivotBlockRetriever createPivotBlockRetriever(
      final int peersToQuery, final long pivotBlockDelta, final int maxRetries) {
    return pivotBlockRetriever =
        spy(
            new PivotBlockRetriever(
                protocolSchedule,
                ethProtocolManager.ethContext(),
                PIVOT_BLOCK_NUMBER,
                peersToQuery,
                pivotBlockDelta,
                maxRetries));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldSucceedWhenAllPeersAgree(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    RespondingEthPeer peer1 = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    RespondingEthPeer peer2 = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    RespondingEthPeer peer3 = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer1.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer1.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer2.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer2.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer3.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer3.getEthPeer())));

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldIgnorePeersThatDoNotHaveThePivotBlock(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    pivotBlockRetriever = createPivotBlockRetriever(3, 1, 1);

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer badPeerA = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);
    final RespondingEthPeer badPeerB = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(badPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(badPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(badPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(badPeerB.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerC.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerC.getEthPeer())));

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldQueryBestPeersFirst(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    pivotBlockRetriever = createPivotBlockRetriever(2, 1, 1);

    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(500), 500);
    final RespondingEthPeer peerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerC.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerC.getEthPeer())));

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldRecoverFromUnresponsivePeer(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    pivotBlockRetriever = createPivotBlockRetriever(2, 1, 1);

    final RespondingEthPeer peerA = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer peerB = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer peerC = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 500);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.TIMEOUT,
                List.of(peerB.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerC.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerC.getEthPeer())));

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldRetryWhenPeersDisagreeOnPivot_successfulRetry(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final long pivotBlockDelta = 1;
    pivotBlockRetriever = createPivotBlockRetriever(2, pivotBlockDelta, 1);

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Only return inconsistent block on the first round
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(9).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        new BlockHeaderTestFixture()
                            .number(10)
                            .extraData(Bytes.of(1))
                            .buildHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(9).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getEthPeer())));

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER - 1).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockRetrieverTestArguments.class)
  public void shouldRetryWhenPeersDisagreeOnPivot_exceedMaxRetries(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final long pivotBlockDelta = 1;
    pivotBlockRetriever = createPivotBlockRetriever(2, pivotBlockDelta, 1);

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(10).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(9).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        new BlockHeaderTestFixture()
                            .number(10)
                            .extraData(Bytes.of(1))
                            .buildHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        new BlockHeaderTestFixture()
                            .number(9)
                            .extraData(Bytes.of(1))
                            .buildHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getEthPeer())));

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasRootCauseInstanceOf(SyncException.class)
        .extracting(e -> ((SyncException) ExceptionUtils.rootCause(e)).getError())
        .isEqualTo(SyncError.PIVOT_BLOCK_HEADER_MISMATCH);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
