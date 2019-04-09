/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.CompleteBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.PersistBlockTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FullSyncBlockHandler<C> implements BlockHandler<Block> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public FullSyncBlockHandler(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<Block>> validateAndImportBlocks(final List<Block> blocks) {
    LOG.debug(
        "Validating and importing {} to {}",
        blocks.get(0).getHeader().getNumber(),
        blocks.get(blocks.size() - 1).getHeader().getNumber());
    return PersistBlockTask.forSequentialBlocks(
            protocolSchedule,
            protocolContext,
            blocks,
            HeaderValidationMode.SKIP_DETACHED,
            metricsSystem)
        .get();
  }

  @Override
  public CompletableFuture<List<Block>> downloadBlocks(final List<BlockHeader> headers) {
    return CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, headers, metricsSystem)
        .run();
  }

  @Override
  public long extractBlockNumber(final Block block) {
    return block.getHeader().getNumber();
  }

  @Override
  public Hash extractBlockHash(final Block block) {
    return block.getHash();
  }

  @Override
  public CompletableFuture<Void> executeParallelCalculations(final List<Block> blocks) {
    final EthScheduler ethScheduler = ethContext.getScheduler();
    final List<CompletableFuture<?>> calculations = new ArrayList<>();
    for (final Block block : blocks) {
      for (final Transaction tx : block.getBody().getTransactions()) {
        calculations.add(ethScheduler.scheduleComputationTask(tx::getSender));
      }
    }
    return CompletableFuture.allOf(calculations.toArray(new CompletableFuture<?>[0]));
  }
}
