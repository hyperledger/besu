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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.hyperledger.besu.util.FutureUtils.completedExceptionally;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Downloads a block from a peer. Will complete exceptionally if block cannot be downloaded. */
public class GetBlockFromPeerTask extends AbstractPeerTask<Block> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<?> protocolSchedule;
  private final Hash hash;
  private final long blockNumber;
  private final MetricsSystem metricsSystem;

  protected GetBlockFromPeerTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem);
    this.blockNumber = blockNumber;
    this.metricsSystem = metricsSystem;
    this.protocolSchedule = protocolSchedule;
    this.hash = hash;
  }

  public static GetBlockFromPeerTask create(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    return new GetBlockFromPeerTask(protocolSchedule, ethContext, hash, blockNumber, metricsSystem);
  }

  @Override
  protected void executeTask() {
    LOG.debug(
        "Downloading block {} from peer {}.",
        hash,
        assignedPeer.map(EthPeer::toString).orElse("<any>"));
    downloadHeader()
        .thenCompose(this::completeBlock)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.info(
                    "Failed to download block {} from peer {}.",
                    hash,
                    assignedPeer.map(EthPeer::toString).orElse("<any>"));
                result.get().completeExceptionally(t);
              } else if (r.getResult().isEmpty()) {
                LOG.info("Failed to download block {} from peer {}.", hash, r.getPeer());
                result.get().completeExceptionally(new IncompleteResultsException());
              } else {
                LOG.debug("Successfully downloaded block {} from peer {}.", hash, r.getPeer());
                result.get().complete(new PeerTaskResult<>(r.getPeer(), r.getResult().get(0)));
              }
            });
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeader() {
    return executeSubTask(
        () -> {
          final AbstractGetHeadersFromPeerTask task =
              GetHeadersFromPeerByHashTask.forSingleHash(
                  protocolSchedule, ethContext, hash, blockNumber, metricsSystem);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run();
        });
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> completeBlock(
      final PeerTaskResult<List<BlockHeader>> headerResult) {
    if (headerResult.getResult().isEmpty()) {
      return completedExceptionally(new IncompleteResultsException());
    }

    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask<?> task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, headerResult.getResult(), metricsSystem);
          task.assignPeer(headerResult.getPeer());
          return task.run();
        });
  }
}
