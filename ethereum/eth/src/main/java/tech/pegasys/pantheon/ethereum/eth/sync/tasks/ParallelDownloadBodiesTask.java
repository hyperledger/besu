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

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPipelinedPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelDownloadBodiesTask<B>
    extends AbstractPipelinedPeerTask<List<BlockHeader>, List<B>> {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockHandler<B> blockHandler;

  ParallelDownloadBodiesTask(
      final BlockHandler<B> blockHandler,
      final BlockingQueue<List<BlockHeader>> inboundQueue,
      final int outboundBacklogSize,
      final EthContext ethContext,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(inboundQueue, outboundBacklogSize, ethContext, ethTasksTimer);

    this.blockHandler = blockHandler;
  }

  @Override
  protected Optional<List<B>> processStep(
      final List<BlockHeader> headers,
      final Optional<List<BlockHeader>> previousHeaders,
      final EthPeer peer) {
    LOG.trace(
        "Downloading bodies {} to {}",
        headers.get(0).getNumber(),
        headers.get(headers.size() - 1).getNumber());
    try {
      final List<B> blocks = blockHandler.downloadBlocks(headers).get();
      LOG.debug(
          "Downloaded bodies {} to {}",
          headers.get(0).getNumber(),
          headers.get(headers.size() - 1).getNumber());
      return Optional.of(blocks);
    } catch (final InterruptedException | ExecutionException e) {
      failExceptionally(e);
      return Optional.empty();
    }
  }
}
