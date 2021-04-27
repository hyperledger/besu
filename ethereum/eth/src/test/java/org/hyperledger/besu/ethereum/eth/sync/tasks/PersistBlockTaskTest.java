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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class PersistBlockTaskTest {

  private BlockchainSetupUtil blockchainUtil;
  private ProtocolSchedule protocolSchedule;
  private ProtocolContext protocolContext;
  private EthContext ethContext;
  private MutableBlockchain blockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  private final DataStorageFormat storageFormat;

  public PersistBlockTaskTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setup() {
    blockchainUtil = BlockchainSetupUtil.forTesting(storageFormat);
    protocolSchedule = blockchainUtil.getProtocolSchedule();
    protocolContext = blockchainUtil.getProtocolContext();
    blockchain = blockchainUtil.getBlockchain();
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    ethContext = ethProtocolManager.ethContext();
  }

  @Test
  public void importsValidBlock() throws Exception {
    blockchainUtil.importFirstBlocks(3);
    final Block nextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    // Create task
    final PersistBlockTask task =
        PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            nextBlock,
            HeaderValidationMode.FULL,
            metricsSystem);
    final CompletableFuture<Block> result = task.run();

    Awaitility.await().atMost(30, SECONDS).until(result::isDone);

    assertThat(result.isCompletedExceptionally()).isFalse();
    assertThat(result.get()).isEqualTo(nextBlock);
    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
  }

  @Test
  public void failsToImportInvalidBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final Block nextBlock = gen.block();

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    // Create task
    final PersistBlockTask task =
        PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            nextBlock,
            HeaderValidationMode.FULL,
            metricsSystem);
    final CompletableFuture<Block> result = task.run();

    Awaitility.await().atMost(30, SECONDS).until(result::isDone);

    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(result::get).hasCauseInstanceOf(InvalidBlockException.class);
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }

  @Test
  public void importsValidBlockSequence() throws Exception {
    blockchainUtil.importFirstBlocks(3);
    final List<Block> nextBlocks =
        Arrays.asList(blockchainUtil.getBlock(3), blockchainUtil.getBlock(4));

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forSequentialBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isFalse();
    assertThat(task.get()).isEqualTo(nextBlocks);
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    }
  }

  @Test
  public void failsToImportInvalidBlockSequenceWhereSecondBlockFails() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final List<Block> nextBlocks = Arrays.asList(blockchainUtil.getBlock(3), gen.block());

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forSequentialBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(task::get).hasCauseInstanceOf(InvalidBlockException.class);
    assertThat(blockchain.contains(nextBlocks.get(0).getHash())).isTrue();
    assertThat(blockchain.contains(nextBlocks.get(1).getHash())).isFalse();
  }

  @Test
  public void failsToImportInvalidBlockSequenceWhereFirstBlockFails() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final List<Block> nextBlocks = Arrays.asList(gen.block(), blockchainUtil.getBlock(3));

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forSequentialBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(task::get).hasCauseInstanceOf(InvalidBlockException.class);
    assertThat(blockchain.contains(nextBlocks.get(0).getHash())).isFalse();
    assertThat(blockchain.contains(nextBlocks.get(1).getHash())).isFalse();
  }

  @Test
  public void importsValidUnorderedBlocks() throws Exception {
    blockchainUtil.importFirstBlocks(3);
    final Block valid = blockchainUtil.getBlock(3);
    final List<Block> nextBlocks = Collections.singletonList(valid);

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forUnorderedBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isFalse();
    assertThat(task.get().size()).isEqualTo(1);
    assertThat(task.get().contains(valid)).isTrue();
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    }
  }

  @Test
  public void importsInvalidUnorderedBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final Block invalid = gen.block();
    final List<Block> nextBlocks = Collections.singletonList(invalid);

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forUnorderedBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isTrue();
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }
  }

  @Test
  public void importsInvalidUnorderedBlocks() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final List<Block> nextBlocks = Arrays.asList(gen.block(), gen.block());

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forUnorderedBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isTrue();
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }
  }

  @Test
  public void importsUnorderedBlocksWithMixOfValidAndInvalidBlocks() throws Exception {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(3);
    final Block valid = blockchainUtil.getBlock(3);
    final Block invalid = gen.block();
    final List<Block> nextBlocks = Arrays.asList(invalid, valid);

    // Sanity check
    for (final Block nextBlock : nextBlocks) {
      assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    }

    // Create task
    final CompletableFuture<List<Block>> task =
        PersistBlockTask.forUnorderedBlocks(
                protocolSchedule,
                protocolContext,
                ethContext,
                nextBlocks,
                HeaderValidationMode.FULL,
                metricsSystem)
            .get();

    Awaitility.await().atMost(30, SECONDS).until(task::isDone);

    assertThat(task.isCompletedExceptionally()).isFalse();
    assertThat(task.get().size()).isEqualTo(1);
    assertThat(task.get().contains(valid)).isTrue();
    assertThat(blockchain.contains(valid.getHash())).isTrue();
    assertThat(blockchain.contains(invalid.getHash())).isFalse();
  }

  @Test
  public void cancelBeforeRunning() {
    blockchainUtil.importFirstBlocks(3);
    final Block nextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    // Create task
    final PersistBlockTask task =
        PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            nextBlock,
            HeaderValidationMode.FULL,
            metricsSystem);

    task.cancel();
    final CompletableFuture<Block> result = task.run();

    assertThat(result.isCancelled()).isTrue();
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }

  @Test
  public void cancelAfterRunning() {
    blockchainUtil.importFirstBlocks(3);
    final Block nextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    // Create task
    final PersistBlockTask task =
        PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            nextBlock,
            HeaderValidationMode.FULL,
            metricsSystem);
    final PersistBlockTask taskSpy = Mockito.spy(task);
    Mockito.doNothing().when(taskSpy).executeTaskTimed();

    final CompletableFuture<Block> result = taskSpy.run();
    taskSpy.cancel();

    assertThat(result.isCancelled()).isTrue();
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }
}
