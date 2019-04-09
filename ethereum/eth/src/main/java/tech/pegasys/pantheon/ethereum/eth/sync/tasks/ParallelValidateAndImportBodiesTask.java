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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPipelinedTask;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelValidateAndImportBodiesTask<B>
    extends AbstractPipelinedTask<List<B>, List<Hash>> {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockHandler<B> blockHandler;

  ParallelValidateAndImportBodiesTask(
      final BlockHandler<B> blockHandler,
      final BlockingQueue<List<B>> inboundQueue,
      final int outboundBacklogSize,
      final MetricsSystem metricsSystem) {
    super(inboundQueue, outboundBacklogSize, metricsSystem);

    this.blockHandler = blockHandler;
  }

  @Override
  protected Optional<List<Hash>> processStep(
      final List<B> blocks, final Optional<List<B>> previousBlocks) {
    final long firstBlock = blockHandler.extractBlockNumber(blocks.get(0));
    final long lastBlock = blockHandler.extractBlockNumber(blocks.get(blocks.size() - 1));
    LOG.debug("Starting import of chain segment {} to {}", firstBlock, lastBlock);
    final CompletableFuture<List<B>> importedBlocksFuture =
        blockHandler.validateAndImportBlocks(blocks);
    try {
      final List<Hash> downloadedHashes =
          importedBlocksFuture.get().stream()
              .map(blockHandler::extractBlockHash)
              .collect(Collectors.toList());
      LOG.info("Completed importing chain segment {} to {}", firstBlock, lastBlock);
      return Optional.of(downloadedHashes);
    } catch (final InterruptedException | ExecutionException e) {
      failExceptionally(e);
      return Optional.empty();
    }
  }
}
