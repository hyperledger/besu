/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.IncompleteResultsException;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Downloads a block from a peer. Will complete exceptionally if block cannot be downloaded. */
public class GetBlockFromPeerTask extends AbstractPeerTask<Block> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<?> protocolSchedule;
  private final Hash hash;

  protected GetBlockFromPeerTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(ethContext, ethTasksTimer);
    this.protocolSchedule = protocolSchedule;
    this.hash = hash;
  }

  public static GetBlockFromPeerTask create(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    return new GetBlockFromPeerTask(protocolSchedule, ethContext, hash, ethTasksTimer);
  }

  @Override
  protected void executeTaskWithPeer(final EthPeer peer) throws PeerNotConnected {
    LOG.debug("Downloading block {} from peer {}.", hash, peer);
    downloadHeader(peer)
        .thenCompose(this::completeBlock)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.info("Failed to download block {} from peer {}.", hash, peer);
                result.get().completeExceptionally(t);
              } else if (r.getResult().isEmpty()) {
                LOG.info("Failed to download block {} from peer {}.", hash, peer);
                result.get().completeExceptionally(new IncompleteResultsException());
              } else {
                LOG.debug("Successfully downloaded block {} from peer {}.", hash, peer);
                result.get().complete(new PeerTaskResult<>(r.getPeer(), r.getResult().get(0)));
              }
            });
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeader(final EthPeer peer) {
    return executeSubTask(
        () ->
            GetHeadersFromPeerByHashTask.forSingleHash(
                    protocolSchedule, ethContext, hash, ethTasksTimer)
                .assignPeer(peer)
                .run());
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> completeBlock(
      final PeerTaskResult<List<BlockHeader>> headerResult) {
    if (headerResult.getResult().isEmpty()) {
      final CompletableFuture<PeerTaskResult<List<Block>>> future = new CompletableFuture<>();
      future.completeExceptionally(new IncompleteResultsException());
      return future;
    }

    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask<?> task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, headerResult.getResult(), ethTasksTimer);
          task.assignPeer(headerResult.getPeer());
          return task.run();
        });
  }
}
