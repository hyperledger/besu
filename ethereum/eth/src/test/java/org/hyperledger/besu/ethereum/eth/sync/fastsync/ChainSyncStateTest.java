/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChainSyncStateTest {

  private BlockHeader pivotBlockHeader;
  private BlockHeader checkpointBlockHeader;
  private BlockHeader genesisBlockHeader;
  private BlockHeader chainHeadHeader;

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthScheduler scheduler;
  @Mock private PeerTaskExecutor peerTaskExecutor;
  @Mock private EthPeer ethPeer;

  @BeforeEach
  public void setUp() {
    pivotBlockHeader = new BlockHeaderTestFixture().number(1000).buildHeader();
    checkpointBlockHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    genesisBlockHeader = new BlockHeaderTestFixture().number(0).buildHeader();
    chainHeadHeader = new BlockHeaderTestFixture().number(600).buildHeader();
  }

  // Factory Method Tests

  @Test
  public void initialSyncShouldCreateStateWithAllFields() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    assertThat(state.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
    assertThat(state.blockDownloadAnchor()).isEqualTo(checkpointBlockHeader);
    assertThat(state.headerDownloadAnchor()).isEqualTo(genesisBlockHeader);
    assertThat(state.headersDownloadComplete()).isFalse();
  }

  @Test
  public void continueToNewPivotShouldUpdatePivotAndAnchor() {
    ChainSyncState initialState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    BlockHeader newPivot = new BlockHeaderTestFixture().number(2000).buildHeader();

    ChainSyncState continuedState = initialState.continueToNewPivot(newPivot, pivotBlockHeader);

    assertThat(continuedState.pivotBlockHeader()).isEqualTo(newPivot);
    assertThat(continuedState.blockDownloadAnchor()).isEqualTo(pivotBlockHeader);
    assertThat(continuedState.headerDownloadAnchor()).isNull();
    assertThat(continuedState.headersDownloadComplete()).isFalse();
  }

  @Test
  public void withHeadersDownloadCompleteShouldMarkComplete() {
    ChainSyncState initialState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    ChainSyncState completedState = initialState.withHeadersDownloadComplete();

    assertThat(completedState.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
    assertThat(completedState.blockDownloadAnchor()).isEqualTo(checkpointBlockHeader);
    assertThat(completedState.headerDownloadAnchor()).isNull();
    assertThat(completedState.headersDownloadComplete()).isTrue();
  }

  @Test
  public void fromHeadShouldUpdateBlockDownloadAnchor() {
    ChainSyncState initialState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    ChainSyncState restartedState = initialState.fromHead(chainHeadHeader);

    assertThat(restartedState.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
    assertThat(restartedState.blockDownloadAnchor()).isEqualTo(chainHeadHeader);
    assertThat(restartedState.headerDownloadAnchor()).isEqualTo(genesisBlockHeader);
    assertThat(restartedState.headersDownloadComplete()).isFalse();
  }

  @Test
  public void fromHeadShouldPreserveHeadersDownloadComplete() {
    ChainSyncState initialState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    ChainSyncState completedState = initialState.withHeadersDownloadComplete();

    ChainSyncState restartedState = completedState.fromHead(chainHeadHeader);

    assertThat(restartedState.headersDownloadComplete()).isTrue();
  }

  @Test
  public void shouldHandleStateTransitionWorkflow() {
    // Initial sync
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    assertThat(state1.headersDownloadComplete()).isFalse();
    assertThat(state1.headerDownloadAnchor()).isEqualTo(genesisBlockHeader);

    // Mark headers complete
    ChainSyncState state2 = state1.withHeadersDownloadComplete();
    assertThat(state2.headersDownloadComplete()).isTrue();
    assertThat(state2.headerDownloadAnchor()).isNull();

    // Continue to new pivot
    BlockHeader newPivot = new BlockHeaderTestFixture().number(2000).buildHeader();
    ChainSyncState state3 = state2.continueToNewPivot(newPivot, pivotBlockHeader);
    assertThat(state3.pivotBlockHeader()).isEqualTo(newPivot);
    assertThat(state3.blockDownloadAnchor()).isEqualTo(pivotBlockHeader);
    assertThat(state3.headersDownloadComplete()).isFalse();

    // Restart from head
    ChainSyncState state4 = state3.fromHead(chainHeadHeader);
    assertThat(state4.blockDownloadAnchor()).isEqualTo(chainHeadHeader);
    assertThat(state4.pivotBlockHeader()).isEqualTo(newPivot);
  }

  // Equality and HashCode Tests

  @Test
  public void shouldBeEqualWhenAllFieldsMatch() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    ChainSyncState state2 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    assertThat(state1).isEqualTo(state2);
    assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
  }

  @Test
  public void shouldNotBeEqualWhenPivotDiffers() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    BlockHeader differentPivot = new BlockHeaderTestFixture().number(2000).buildHeader();
    ChainSyncState state2 =
        ChainSyncState.initialSync(differentPivot, checkpointBlockHeader, genesisBlockHeader);

    assertThat(state1).isNotEqualTo(state2);
  }

  @Test
  public void shouldNotBeEqualWhenBlockDownloadAnchorDiffers() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    BlockHeader differentAnchor = new BlockHeaderTestFixture().number(600).buildHeader();
    ChainSyncState state2 =
        ChainSyncState.initialSync(pivotBlockHeader, differentAnchor, genesisBlockHeader);

    assertThat(state1).isNotEqualTo(state2);
  }

  @Test
  public void shouldNotBeEqualWhenHeaderDownloadAnchorDiffers() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    BlockHeader differentHeader = new BlockHeaderTestFixture().number(10).buildHeader();
    ChainSyncState state2 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, differentHeader);

    assertThat(state1).isNotEqualTo(state2);
  }

  @Test
  public void shouldNotBeEqualWhenHeadersDownloadCompleteDiffers() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    ChainSyncState state2 = state1.withHeadersDownloadComplete();

    assertThat(state1).isNotEqualTo(state2);
  }

  @Test
  public void shouldBeEqualWhenBothHeaderDownloadAnchorsAreNull() {
    ChainSyncState state1 = new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);
    ChainSyncState state2 = new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);

    assertThat(state1).isEqualTo(state2);
    assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
  }

  @Test
  public void shouldNotBeEqualWhenOneHeaderDownloadAnchorIsNull() {
    ChainSyncState state1 = new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);
    ChainSyncState state2 =
        new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader, true);

    assertThat(state1).isNotEqualTo(state2);
  }

  @Test
  public void shouldNotBeEqualToNull() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    assertThat(state.equals(null)).isFalse();
  }

  // ToString Tests

  @Test
  public void toStringShouldIncludeAllFields() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    String result = state.toString();

    assertThat(result).contains("pivotBlockNumber=" + pivotBlockHeader.getNumber());
    assertThat(result).contains("pivotBlockHash=" + pivotBlockHeader.getHash());
    assertThat(result).contains("checkpointBlockNumber=" + checkpointBlockHeader.getNumber());
    assertThat(result).contains("headerDownloadAnchorNumber=" + genesisBlockHeader.getNumber());
    assertThat(result).contains("headersDownloadComplete=false");
  }

  @Test
  public void toStringShouldHandleNullHeaderDownloadAnchor() {
    // withHeadersDownloadComplete() sets headerDownloadAnchor to null
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader)
            .withHeadersDownloadComplete();

    String result = state.toString();

    assertThat(result).contains("headerDownloadAnchorNumber=null");
    assertThat(result).contains("headersDownloadComplete=true");
  }

  // downloadCheckpointHeader Tests

  @Test
  @SuppressWarnings("unchecked")
  public void downloadCheckpointHeaderShouldReturnHeaderOnSuccess() {
    setupMocksForDownloadCheckpointHeader();

    BlockHeader expectedHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(List.of(expectedHeader)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.executeAgainstPeer(any(GetHeadersFromPeerTask.class), eq(ethPeer)))
        .thenReturn(successResult);

    BlockHeader result =
        ChainSyncState.downloadCheckpointHeader(protocolSchedule, ethContext, Hash.ZERO);

    assertThat(result).isEqualTo(expectedHeader);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void downloadCheckpointHeaderShouldUseCorrectTaskParameters() {
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(scheduler);
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);
    when(ethPeers.getMaxPeers()).thenReturn(25);
    when(ethPeers.waitForPeer(any(Predicate.class)))
        .thenReturn(CompletableFuture.completedFuture(ethPeer));

    BlockHeader expectedHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(List.of(expectedHeader)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.executeAgainstPeer(any(GetHeadersFromPeerTask.class), eq(ethPeer)))
        .thenReturn(successResult);

    when(scheduler.scheduleServiceTask(any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<CompletableFuture<?>> supplier =
                  invocation.getArgument(0);
              return supplier.get();
            });

    Hash testHash =
        Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    BlockHeader result =
        ChainSyncState.downloadCheckpointHeader(protocolSchedule, ethContext, testHash);

    assertThat(result).isEqualTo(expectedHeader);
    verify(ethPeers).getMaxPeers();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void downloadCheckpointHeaderShouldFailOnInternalServerError() {
    setupMocksForDownloadCheckpointHeader();

    PeerTaskExecutorResult<List<BlockHeader>> errorResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR,
            Collections.emptyList());

    when(peerTaskExecutor.executeAgainstPeer(any(GetHeadersFromPeerTask.class), eq(ethPeer)))
        .thenReturn(errorResult);

    assertThatThrownBy(
            () -> ChainSyncState.downloadCheckpointHeader(protocolSchedule, ethContext, Hash.ZERO))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected internal issue");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void downloadCheckpointHeaderShouldWaitForPeerWithCorrectFilter() {
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(scheduler);
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);
    when(ethPeers.getMaxPeers()).thenReturn(25);

    // Capture the peer filter predicate
    when(ethPeers.waitForPeer(any(Predicate.class)))
        .thenReturn(CompletableFuture.completedFuture(ethPeer));

    BlockHeader expectedHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(List.of(expectedHeader)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.executeAgainstPeer(any(GetHeadersFromPeerTask.class), eq(ethPeer)))
        .thenReturn(successResult);

    when(scheduler.scheduleServiceTask(any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<CompletableFuture<?>> supplier =
                  invocation.getArgument(0);
              return supplier.get();
            });

    ChainSyncState.downloadCheckpointHeader(protocolSchedule, ethContext, Hash.ZERO);

    verify(ethPeers).waitForPeer(any(Predicate.class));
  }

  // Immutability Tests

  @Test
  public void factoryMethodsShouldReturnNewInstances() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    ChainSyncState state2 = state1.withHeadersDownloadComplete();
    ChainSyncState state3 = state2.fromHead(chainHeadHeader);

    // All should be different instances
    assertThat(state1).isNotSameAs(state2);
    assertThat(state2).isNotSameAs(state3);
    assertThat(state1).isNotSameAs(state3);

    // Original state should be unchanged
    assertThat(state1.headersDownloadComplete()).isFalse();
    assertThat(state1.headerDownloadAnchor()).isEqualTo(genesisBlockHeader);
  }

  // Edge Case Tests

  @Test
  public void shouldHandleMultipleContinuations() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    BlockHeader pivot2 = new BlockHeaderTestFixture().number(2000).buildHeader();
    ChainSyncState continued1 = state.continueToNewPivot(pivot2, pivotBlockHeader);

    BlockHeader pivot3 = new BlockHeaderTestFixture().number(3000).buildHeader();
    ChainSyncState continued2 = continued1.continueToNewPivot(pivot3, pivot2);

    assertThat(continued2.pivotBlockHeader()).isEqualTo(pivot3);
    assertThat(continued2.blockDownloadAnchor()).isEqualTo(pivot2);
    assertThat(continued2.headerDownloadAnchor()).isNull();
    assertThat(continued2.headersDownloadComplete()).isFalse();
  }

  @Test
  public void shouldHandleSamePivotAndCheckpoint() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, pivotBlockHeader, genesisBlockHeader);

    assertThat(state.pivotBlockHeader()).isEqualTo(state.blockDownloadAnchor());
  }

  @Test
  public void shouldHandleVeryLargeBlockNumbers() {
    BlockHeader largePivot =
        new BlockHeaderTestFixture().number(Long.MAX_VALUE - 100).buildHeader();
    BlockHeader largeCheckpoint =
        new BlockHeaderTestFixture().number(Long.MAX_VALUE - 500).buildHeader();

    ChainSyncState state =
        ChainSyncState.initialSync(largePivot, largeCheckpoint, genesisBlockHeader);

    assertThat(state.pivotBlockHeader().getNumber()).isEqualTo(Long.MAX_VALUE - 100);
    assertThat(state.blockDownloadAnchor().getNumber()).isEqualTo(Long.MAX_VALUE - 500);
  }

  @Test
  public void hashCodeShouldBeConsistentAcrossMultipleCalls() {
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    int hash1 = state.hashCode();
    int hash2 = state.hashCode();
    int hash3 = state.hashCode();

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash2).isEqualTo(hash3);
  }

  @Test
  public void equalStatesShouldHaveSameHashCode() {
    ChainSyncState state1 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    ChainSyncState state2 =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    assertThat(state1).isEqualTo(state2);
    assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
  }

  @Test
  public void shouldHandleNullHeaderDownloadAnchorInEquality() {
    ChainSyncState state1 = new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);
    ChainSyncState state2 = new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);

    assertThat(state1).isEqualTo(state2);
    assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
  }

  @SuppressWarnings("unchecked")
  private void setupMocksForDownloadCheckpointHeader() {
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(scheduler);
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);

    when(ethPeers.getMaxPeers()).thenReturn(25);
    when(ethPeers.waitForPeer(any(Predicate.class)))
        .thenReturn(CompletableFuture.completedFuture(ethPeer));

    lenient().doNothing().when(ethPeer).recordUselessResponse(any());

    // Scheduler just executes tasks immediately for testing
    when(scheduler.scheduleServiceTask(any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<CompletableFuture<?>> supplier =
                  invocation.getArgument(0);
              return supplier.get();
            });
  }
}
