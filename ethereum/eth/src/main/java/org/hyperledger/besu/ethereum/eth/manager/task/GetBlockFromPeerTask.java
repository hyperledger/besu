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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Downloads a block from a peer. Will complete exceptionally if block cannot be downloaded. */
public class GetBlockFromPeerTask extends AbstractPeerTask<Block> {
  private static final Logger LOG = LoggerFactory.getLogger(GetBlockFromPeerTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final Optional<Hash> hash;
  private final long blockNumber;
  private final MetricsSystem metricsSystem;

  protected GetBlockFromPeerTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Optional<Hash> hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem);
    this.blockNumber = blockNumber;
    this.metricsSystem = metricsSystem;
    this.protocolSchedule = protocolSchedule;
    this.hash = hash;
  }

  public static GetBlockFromPeerTask create(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Optional<Hash> hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    return new GetBlockFromPeerTask(protocolSchedule, ethContext, hash, blockNumber, metricsSystem);
  }

  @Override
  protected void executeTask() {
    final String blockIdentifier = blockNumber + " (" + hash + ")";
    LOG.debug(
        "Downloading block {} from peer {}.",
        blockIdentifier,
        assignedPeer.map(EthPeer::toString).orElse("<any>"));
    downloadHeader()
        .thenCompose(this::completeBlock)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.debug(
                    "Failed to download block {} from peer {} with message '{}' and cause '{}'",
                    blockIdentifier,
                    assignedPeer.map(EthPeer::toString).orElse("<any>"),
                    t.getMessage(),
                    t.getCause());
                result.completeExceptionally(t);
              } else if (r.getResult().isEmpty()) {
                r.getPeer().recordUselessResponse("Download block returned an empty result");
                LOG.debug(
                    "Failed to download block {} from peer {} with empty result.",
                    blockIdentifier,
                    r.getPeer());
                result.completeExceptionally(new IncompleteResultsException());
              } else {
                LOG.debug(
                    "Successfully downloaded block {} from peer {}.", blockIdentifier, r.getPeer());
                result.complete(new PeerTaskResult<>(r.getPeer(), r.getResult().get(0)));
              }
            });
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeader() {
    return executeSubTask(
        () -> {
          final AbstractGetHeadersFromPeerTask task;
          task =
              hash.map(
                      value ->
                          GetHeadersFromPeerByHashTask.forSingleHash(
                              protocolSchedule, ethContext, value, metricsSystem))
                  .orElseGet(
                      () ->
                          GetHeadersFromPeerByNumberTask.forSingleNumber(
                              protocolSchedule, ethContext, blockNumber, metricsSystem));
          assignedPeer.ifPresent(task::assignPeer);
          return task.run();
        });
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> completeBlock(
      final PeerTaskResult<List<BlockHeader>> headerResult) {
    if (headerResult.getResult().isEmpty()) {
      LOG.debug("header result is empty.");
      return CompletableFuture.failedFuture(new IncompleteResultsException());
    }

    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, headerResult.getResult(), metricsSystem);
          task.assignPeer(headerResult.getPeer());
          return task.run();
        });
  }
}
