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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeaders;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRange;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DownloadHeadersStepTest {

  private static final int HEADER_REQUEST_SIZE = 200;
  private static ProtocolSchedule protocolSchedule;
  private static ProtocolContext protocolContext;
  private static MutableBlockchain blockchain;

  private final EthPeer syncTarget = mock(EthPeer.class);
  private EthProtocolManager ethProtocolManager;
  private PeerTaskExecutor peerTaskExecutor;
  private DownloadHeadersStep downloader;
  private SyncTargetRange checkpointRange;

  @BeforeAll
  public static void setUpClass() {
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    setupUtil.importFirstBlocks(20);
    protocolSchedule = setupUtil.getProtocolSchedule();
    protocolContext = setupUtil.getProtocolContext();
    blockchain = protocolContext.getBlockchain();
  }

  @BeforeEach
  public void setUp() {
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setBlockchain(blockchain)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();

    checkpointRange =
        new SyncTargetRange(
            syncTarget, blockchain.getBlockHeader(1).get(), blockchain.getBlockHeader(10).get());
  }

  @Test
  public void shouldRetrieveHeadersForCheckpointRange() {
    downloader =
        new DownloadHeadersStep(
            protocolSchedule, ethProtocolManager.ethContext(), HEADER_REQUEST_SIZE);

    GetHeadersFromPeerTask task1 =
        new GetHeadersFromPeerTask(2, 8, 0, Direction.FORWARD, protocolSchedule);
    when(peerTaskExecutor.execute(task1))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(headersFromChain(2, 6)),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()));
    GetHeadersFromPeerTask task2 =
        new GetHeadersFromPeerTask(7, 3, 0, Direction.FORWARD, protocolSchedule);
    when(peerTaskExecutor.execute(task2))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(headersFromChain(7, 9)),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()));
    final CompletableFuture<RangeHeaders> result = downloader.apply(checkpointRange);

    // The start of the range should have been imported as part of the previous batch hence 2-10.
    assertThat(result)
        .isCompletedWithValue(new RangeHeaders(checkpointRange, headersFromChain(2, 10)));
  }

  @Test
  public void shouldCancelRequestToPeerWhenReturnedFutureIsCancelled() {
    downloader =
        new DownloadHeadersStep(
            protocolSchedule, ethProtocolManager.ethContext(), HEADER_REQUEST_SIZE);

    final CompletableFuture<RangeHeaders> result = this.downloader.apply(checkpointRange);

    result.cancel(true);

    assertThat(EthProtocolManagerTestUtil.getPendingFuturesCount(ethProtocolManager)).isZero();
  }

  @Test
  public void shouldReturnOnlyEndHeaderWhenCheckpointRangeHasLengthOfOne() {
    downloader =
        new DownloadHeadersStep(
            protocolSchedule, ethProtocolManager.ethContext(), HEADER_REQUEST_SIZE);
    final SyncTargetRange checkpointRange =
        new SyncTargetRange(
            syncTarget, blockchain.getBlockHeader(3).get(), blockchain.getBlockHeader(4).get());

    final CompletableFuture<RangeHeaders> result = this.downloader.apply(checkpointRange);

    assertThat(result)
        .isCompletedWithValue(new RangeHeaders(checkpointRange, headersFromChain(4, 4)));
  }

  @Test
  public void shouldGetRemainingHeadersWhenRangeHasNoEnd() {
    downloader =
        new DownloadHeadersStep(
            protocolSchedule, ethProtocolManager.ethContext(), HEADER_REQUEST_SIZE);
    final SyncTargetRange checkpointRange =
        new SyncTargetRange(null, blockchain.getBlockHeader(3).get());

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(headersFromChain(4, 19)),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()));

    final CompletableFuture<RangeHeaders> result = this.downloader.apply(checkpointRange);

    assertThat(result)
        .isCompletedWithValue(new RangeHeaders(checkpointRange, headersFromChain(4, 19)));
  }

  private List<BlockHeader> headersFromChain(final long startNumber, final long endNumber) {
    final List<BlockHeader> headers = new ArrayList<>();
    for (long i = startNumber; i <= endNumber; i++) {
      headers.add(blockchain.getBlockHeader(i).get());
    }
    return Collections.unmodifiableList(headers);
  }
}
