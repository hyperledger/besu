/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.consensus.common;

import static java.util.Collections.emptyList;
import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SchedulableMiningCoordinatorTest {

  @Mock private BftMiningCoordinator coordinator1;
  @Mock private BftMiningCoordinator coordinator2;
  @Mock private NoopMiningCoordinator noopCoordinator;
  @Mock private Blockchain blockchain;
  @Mock private BlockHeader blockHeader;
  @Mock private BlockBody blockBody;
  private Block block;
  private BlockAddedEvent blockEvent;
  private ForksSchedule<MiningCoordinator> miningCoordinatorSchedule;
  private static final long MIGRATION_BLOCK_NUMBER = 5L;

  @Before
  public void setup() {
    final ForkSpec<MiningCoordinator> genesisFork =
        new ForkSpec<>(GENESIS_BLOCK_NUMBER, coordinator1);
    final ForkSpec<MiningCoordinator> migrationFork =
        new ForkSpec<>(MIGRATION_BLOCK_NUMBER, coordinator2);
    miningCoordinatorSchedule = new ForksSchedule<>(genesisFork, List.of(migrationFork));
    this.block = new Block(blockHeader, blockBody);
    blockEvent = BlockAddedEvent.createForHeadAdvancement(this.block, emptyList(), emptyList());
  }

  @Test
  public void startShouldRegisterThisCoordinatorAsObserver() {
    final SchedulableMiningCoordinator coordinator =
        new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain);

    coordinator.start();

    verify(blockchain).observeBlockAdded(coordinator);
  }

  @Test
  public void stopShouldUnregisterThisCoordinatorAsObserver() {
    final SchedulableMiningCoordinator coordinator =
        new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain);
    when(blockchain.observeBlockAdded(coordinator)).thenReturn(1L);

    coordinator.start();
    coordinator.stop();

    verify(blockchain).removeObserver(1L);
  }

  @Test
  public void onBlockAddedShouldMigrateToNextMiningCoordinatorAndDelegate() {
    when(blockHeader.getNumber()).thenReturn(MIGRATION_BLOCK_NUMBER - 1);

    new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain)
        .onBlockAdded(blockEvent);

    verify(coordinator1).stop();
    verify(coordinator2).start();
    verify(coordinator2).onBlockAdded(blockEvent);
  }

  @Test
  public void onBlockAddedShouldNotDelegateWhenDelegateIsNoop() {
    ForksSchedule<MiningCoordinator> noopCoordinatorSchedule =
        new ForksSchedule<>(new ForkSpec<>(GENESIS_BLOCK_NUMBER, noopCoordinator), emptyList());
    when(blockHeader.getNumber()).thenReturn(GENESIS_BLOCK_NUMBER);

    new SchedulableMiningCoordinator(noopCoordinatorSchedule, blockchain).onBlockAdded(blockEvent);

    verifyNoInteractions(noopCoordinator);
  }

  @Test
  public void delegatesToActiveMiningCoordinator() {
    verifyDelegation(MiningCoordinator::start, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(MiningCoordinator::start, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(MiningCoordinator::stop, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(MiningCoordinator::stop, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(MiningCoordinator::enable, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(MiningCoordinator::enable, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(MiningCoordinator::disable, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        MiningCoordinator::disable, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(MiningCoordinator::isMining, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        MiningCoordinator::isMining, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(
        MiningCoordinator::getMinTransactionGasPrice,
        GENESIS_BLOCK_NUMBER,
        coordinator1,
        coordinator2);
    verifyDelegation(
        MiningCoordinator::getMinTransactionGasPrice,
        MIGRATION_BLOCK_NUMBER,
        coordinator2,
        coordinator1);

    verifyDelegation(
        c -> c.setExtraData(Bytes.EMPTY), GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        c -> c.setExtraData(Bytes.EMPTY), MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(
        MiningCoordinator::getCoinbase, GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        MiningCoordinator::getCoinbase, MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(
        c -> c.createBlock(blockHeader, emptyList(), emptyList()),
        GENESIS_BLOCK_NUMBER,
        coordinator1,
        coordinator2);
    verifyDelegation(
        c -> c.createBlock(blockHeader, emptyList(), emptyList()),
        MIGRATION_BLOCK_NUMBER,
        coordinator2,
        coordinator1);

    verifyDelegation(
        c -> c.changeTargetGasLimit(1L), GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        c -> c.changeTargetGasLimit(1L), MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);

    verifyDelegation(
        c -> c.changeTargetGasLimit(1L), GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegation(
        c -> c.changeTargetGasLimit(1L), MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);
  }

  private void verifyDelegation(
      final Consumer<MiningCoordinator> methodUnderTest,
      final long blockHeight,
      final MiningCoordinator expectedActiveCoordinator,
      final MiningCoordinator expectedInactiveCoordinator) {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(blockHeight);

    methodUnderTest.accept(new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain));

    methodUnderTest.accept(verify(expectedActiveCoordinator));
    verifyNoInteractions(expectedInactiveCoordinator);
    reset(coordinator1, coordinator2);
  }

  @Test
  public void verifyDelegationForAwaitStop() throws InterruptedException {
    verifyDelegationForAwaitStop(GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegationForAwaitStop(MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);
  }

  private void verifyDelegationForAwaitStop(
      final long blockHeight,
      final MiningCoordinator expectedActiveCoordinator,
      final MiningCoordinator expectedInactiveCoordinator)
      throws InterruptedException {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(blockHeight);

    new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain).awaitStop();

    verify(expectedActiveCoordinator).awaitStop();
    verifyNoInteractions(expectedInactiveCoordinator);
    reset(coordinator1, coordinator2);
  }

  @Test
  public void verifyDelegationForOnBlockAdded() {
    verifyDelegationForOnBlockAdded(GENESIS_BLOCK_NUMBER, coordinator1, coordinator2);
    verifyDelegationForOnBlockAdded(MIGRATION_BLOCK_NUMBER, coordinator2, coordinator1);
  }

  private void verifyDelegationForOnBlockAdded(
      final long blockHeight,
      final BftMiningCoordinator expectedActiveCoordinator,
      final BftMiningCoordinator expectedInactiveCoordinator) {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(blockHeight);
    when(blockHeader.getNumber()).thenReturn(blockHeight);

    new SchedulableMiningCoordinator(miningCoordinatorSchedule, blockchain)
        .onBlockAdded(blockEvent);

    verify(expectedActiveCoordinator).onBlockAdded(blockEvent);
    verifyNoInteractions(expectedInactiveCoordinator);
    reset(coordinator1, coordinator2);
  }
}
