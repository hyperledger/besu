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

import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPipelinedTask;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ParallelExtractTxSignaturesTask<B> extends AbstractPipelinedTask<List<B>, List<B>> {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockHandler<B> blockHandler;

  ParallelExtractTxSignaturesTask(
      final BlockHandler<B> blockHandler,
      final BlockingQueue<List<B>> inboundQueue,
      final int outboundBacklogSize,
      final MetricsSystem metricsSystem) {
    super(inboundQueue, outboundBacklogSize, metricsSystem);
    this.blockHandler = blockHandler;
  }

  @Override
  protected Optional<List<B>> processStep(
      final List<B> bodies, final Optional<List<B>> previousBodies) {
    LOG.trace(
        "Calculating fields for transactions between {} to {}",
        blockHandler.extractBlockNumber(bodies.get(0)),
        blockHandler.extractBlockNumber(bodies.get(bodies.size() - 1)));

    try {
      blockHandler.executeParallelCalculations(bodies).get();
    } catch (final InterruptedException | ExecutionException e) {
      result.get().completeExceptionally(e);
      return Optional.empty();
    }
    LOG.debug(
        "Calculated fields for transactions between {} to {}",
        blockHandler.extractBlockNumber(bodies.get(0)),
        blockHandler.extractBlockNumber(bodies.get(bodies.size() - 1)));
    return Optional.of(bodies);
  }
}
