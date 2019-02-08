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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPipelinedPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelDownloadHeadersTask<C>
    extends AbstractPipelinedPeerTask<BlockHeader, List<BlockHeader>> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;

  ParallelDownloadHeadersTask(
      final BlockingQueue<BlockHeader> inboundQueue,
      final int outboundBacklogSize,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(inboundQueue, outboundBacklogSize, ethContext, ethTasksTimer);

    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  protected Optional<List<BlockHeader>> processStep(
      final BlockHeader nextCheckpointHeader,
      final Optional<BlockHeader> previousCheckpointHeader,
      final EthPeer peer) {
    if (!previousCheckpointHeader.isPresent()) {
      return Optional.empty();
    }
    final int segmentLength =
        (int) (nextCheckpointHeader.getNumber() - previousCheckpointHeader.get().getNumber()) - 1;
    LOG.trace(
        "Requesting download of {} blocks ending at {}",
        segmentLength,
        nextCheckpointHeader.getHash());
    final DownloadHeaderSequenceTask<C> downloadTask =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            nextCheckpointHeader,
            segmentLength,
            ethTasksTimer);
    downloadTask.assignPeer(peer);
    final CompletableFuture<List<BlockHeader>> headerFuture = executeSubTask(downloadTask::run);

    final List<BlockHeader> headers = Lists.newArrayList(previousCheckpointHeader.get());
    try {
      headers.addAll(headerFuture.get());
    } catch (final InterruptedException | ExecutionException e) {
      result.get().completeExceptionally(e);
      return Optional.empty();
    }
    headers.add(nextCheckpointHeader);
    if (headers.size() > 2) {
      LOG.debug(
          "Downloaded headers {} to {}",
          headers.get(1).getNumber(),
          headers.get(headers.size() - 1).getNumber());
    }
    return Optional.of(headers);
  }
}
