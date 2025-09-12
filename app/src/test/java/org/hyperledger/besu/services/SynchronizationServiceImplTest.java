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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.SyncStatus;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SynchronizationServiceImplTest {

  @Mock private Synchronizer synchronizer;
  @Mock private ProtocolContext protocolContext;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private SyncState syncState;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private SyncStatus syncStatus;
  @Mock private MutableBlockchain blockchain;

  private SynchronizationServiceImpl synchronizationService;

  @BeforeEach
  void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    synchronizationService =
        new SynchronizationServiceImpl(
            synchronizer, protocolContext, protocolSchedule, syncState, worldStateArchive);
  }

  @Test
  void shouldReturnHighestBlockFromSynchronizer() {
    when(syncStatus.getHighestBlock()).thenReturn(100L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));

    Optional<Long> highestBlock = synchronizationService.getHighestBlock();
    assertThat(highestBlock).isPresent();
    assertThat(highestBlock.get()).isEqualTo(100L);
  }

  @Test
  void shouldReturnCurrentBlockFromBlockchain() {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(95L);

    Optional<Long> currentBlock = synchronizationService.getCurrentBlock();
    assertThat(currentBlock).isPresent();
    assertThat(currentBlock.get()).isEqualTo(95L);
  }

  @Test
  void shouldReturnInSyncStatusFromSyncState() {
    when(syncState.isInSync()).thenReturn(true);

    boolean isInSync = synchronizationService.isInSync();
    assertThat(isInSync).isTrue();
  }

  @Test
  void shouldReturnFalseWhenNotInSync() {
    when(syncState.isInSync()).thenReturn(false);

    boolean isInSync = synchronizationService.isInSync();
    assertThat(isInSync).isFalse();
  }

  @Test
  void shouldCalculateBlocksBehindCorrectly() {
    when(syncStatus.getHighestBlock()).thenReturn(100L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));
    when(blockchain.getChainHeadBlockNumber()).thenReturn(95L);

    long blocksBehind = synchronizationService.getBlocksBehind();
    assertThat(blocksBehind).isEqualTo(5L);
  }

  @Test
  void shouldReturnZeroBlocksBehindWhenSynced() {
    when(syncStatus.getHighestBlock()).thenReturn(100L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));
    when(blockchain.getChainHeadBlockNumber()).thenReturn(100L);

    long blocksBehind = synchronizationService.getBlocksBehind();
    assertThat(blocksBehind).isEqualTo(0L);
  }

  @Test
  void shouldHandleMissingSyncStatus() {
    when(synchronizer.getSyncStatus()).thenReturn(Optional.empty());
    when(blockchain.getChainHeadBlockNumber()).thenReturn(100L);

    long blocksBehind = synchronizationService.getBlocksBehind();
    assertThat(blocksBehind).isEqualTo(0L); // Should return 0 when no sync status
  }

  @Test
  void shouldReturnZeroWhenBlockchainIsEmpty() {
    when(syncStatus.getHighestBlock()).thenReturn(0L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));
    when(blockchain.getChainHeadBlockNumber()).thenReturn(0L);

    long blocksBehind = synchronizationService.getBlocksBehind();
    assertThat(blocksBehind).isEqualTo(0L);
  }

  @Test
  void shouldHandleLargeBlockNumbers() {
    when(syncStatus.getHighestBlock()).thenReturn(1000000L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));
    when(blockchain.getChainHeadBlockNumber()).thenReturn(999995L);

    long blocksBehind = synchronizationService.getBlocksBehind();
    assertThat(blocksBehind).isEqualTo(5L);
  }

  @Test
  void shouldReturnCorrectHighestBlockForLargeNumbers() {
    when(syncStatus.getHighestBlock()).thenReturn(1000000L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));

    Optional<Long> highestBlock = synchronizationService.getHighestBlock();
    assertThat(highestBlock).isPresent();
    assertThat(highestBlock.get()).isEqualTo(1000000L);
  }

  @Test
  void shouldReturnCorrectCurrentBlockForLargeNumbers() {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(999995L);

    Optional<Long> currentBlock = synchronizationService.getCurrentBlock();
    assertThat(currentBlock).isPresent();
    assertThat(currentBlock.get()).isEqualTo(999995L);
  }
}
