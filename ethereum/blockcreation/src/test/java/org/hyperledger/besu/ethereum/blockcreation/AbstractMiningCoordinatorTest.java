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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class AbstractMiningCoordinatorTest {

  private static final Block BLOCK =
      new Block(
          new BlockHeaderTestFixture().buildHeader(),
          new BlockBody(Collections.emptyList(), Collections.emptyList()));
  private final Blockchain blockchain = mock(Blockchain.class);
  private final PoWMinerExecutor minerExecutor = mock(PoWMinerExecutor.class);
  private final SyncState syncState = mock(SyncState.class);
  private final PoWBlockMiner blockMiner = mock(PoWBlockMiner.class);
  private final TestMiningCoordinator miningCoordinator =
      new TestMiningCoordinator(blockchain, minerExecutor, syncState);

  @Before
  public void setUp() {
    when(minerExecutor.startAsyncMining(any(), any(), any())).thenReturn(Optional.of(blockMiner));
  }

  @Test
  public void shouldNotStartMiningWhenEnabledAndOutOfSync() {
    when(syncState.isInSync()).thenReturn(false);
    miningCoordinator.enable();
    miningCoordinator.start();
    verifyNoInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldStartMiningWhenEnabledAndInSync() {
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.enable();
    miningCoordinator.start();
    verify(minerExecutor).startAsyncMining(any(), any(), any());
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldStartMiningWhenEnabledAndBecomeInSync() {
    when(syncState.isInSync()).thenReturn(false);
    miningCoordinator.enable();
    miningCoordinator.start();
    verify(minerExecutor, never()).startAsyncMining(any(), any(), any());

    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.inSyncChanged(true);

    verify(minerExecutor).startAsyncMining(any(), any(), any());
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldHaltMiningWhenBecomingOutOfSync() {
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.enable();
    miningCoordinator.start();
    verify(minerExecutor).startAsyncMining(any(), any(), any());

    miningCoordinator.inSyncChanged(false);

    verify(blockMiner).cancel();
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartWhenBlockAddedAndOutOfSync() {
    when(syncState.isInSync()).thenReturn(false);
    miningCoordinator.enable();
    miningCoordinator.start();

    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));

    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldRestartMiningWhenBlockAddedAndInSync() {
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.enable();
    miningCoordinator.start();

    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));

    verify(blockMiner).cancel();
    verify(minerExecutor, times(2)).startAsyncMining(any(), any(), any());

    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBecomingInSyncIfMinerNotStarted() {
    miningCoordinator.enable();
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.inSyncChanged(true);
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBecomingInSyncIfMinerNotEnabled() {
    miningCoordinator.disable();
    miningCoordinator.start();
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.inSyncChanged(true);
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBecomingInSyncIfMinerStopped() {
    miningCoordinator.enable();
    miningCoordinator.start();
    miningCoordinator.stop();
    verify(minerExecutor).shutDown();
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.inSyncChanged(true);

    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBecomingInSyncIfMinerStoppedThenStarted() {
    miningCoordinator.enable();
    miningCoordinator.start();
    miningCoordinator.stop();
    verify(minerExecutor).shutDown();
    miningCoordinator.start();

    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.inSyncChanged(true);
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBlockAddedAndInSyncIfMinerNotEnabled() {
    miningCoordinator.disable();
    miningCoordinator.start();
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));

    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBlockAddedAndInSyncIfMinerNotStarted() {
    miningCoordinator.enable();
    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBlockAddedAndInSyncIfStopped() {
    miningCoordinator.enable();
    miningCoordinator.start();
    miningCoordinator.stop();
    verify(minerExecutor).shutDown();

    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));

    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  @Test
  public void shouldNotStartMiningWhenBlockAddedAndInSyncIfStoppedThenStarted() {
    miningCoordinator.enable();
    miningCoordinator.start();
    miningCoordinator.stop();
    verify(minerExecutor).shutDown();
    miningCoordinator.start();

    when(syncState.isInSync()).thenReturn(true);
    miningCoordinator.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            BLOCK, Collections.emptyList(), Collections.emptyList()));
    verifyNoMoreInteractions(minerExecutor, blockMiner);
  }

  public static class TestMiningCoordinator extends AbstractMiningCoordinator<PoWBlockMiner> {

    public TestMiningCoordinator(
        final Blockchain blockchain,
        final AbstractMinerExecutor<PoWBlockMiner> executor,
        final SyncState syncState) {
      super(blockchain, executor, syncState);
    }

    @Override
    public boolean newChainHeadInvalidatesMiningOperation(final BlockHeader newChainHeadHeader) {
      return true;
    }
  }
}
