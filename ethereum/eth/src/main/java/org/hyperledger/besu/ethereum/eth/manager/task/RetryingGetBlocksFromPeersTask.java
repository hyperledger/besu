/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetBlocksFromPeersTask
    extends AbstractRetryingSwitchingPeerTask<PeerTaskResult<List<Block>>> {

  private static final Logger LOG = LoggerFactory.getLogger(RetryingGetBlocksFromPeersTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final List<BlockHeader> headers;

  protected RetryingGetBlocksFromPeersTask(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final List<BlockHeader> headers) {
    super(ethContext, metricsSystem, Objects::isNull, maxRetries);
    this.protocolSchedule = protocolSchedule;
    this.headers = headers;
  }

  public static RetryingGetBlocksFromPeersTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final List<BlockHeader> headers) {
    return new RetryingGetBlocksFromPeersTask(
        ethContext, protocolSchedule, metricsSystem, maxRetries, headers);
  }

  @Override
  protected CompletableFuture<PeerTaskResult<List<Block>>> executeTaskOnCurrentPeer(
      final EthPeer currentPeer) {
    final GetBodiesFromPeerTask getBodiesTask =
        GetBodiesFromPeerTask.forHeaders(
            protocolSchedule, getEthContext(), headers, getMetricsSystem());
    getBodiesTask.assignPeer(currentPeer);

    return executeSubTask(getBodiesTask::run)
        .thenApply(
            peerResult -> {
              LOG.atDebug()
                  .setMessage("Got {} blocks from peer {}, attempt {}")
                  .addArgument(peerResult.getResult()::size)
                  .addArgument(peerResult.getPeer())
                  .addArgument(this::getRetryCount)
                  .log();

              if (peerResult.getResult().isEmpty()) {
                currentPeer.recordUselessResponse("GetBodiesFromPeerTask");
                throw new IncompleteResultsException(
                    "No blocks returned by peer " + currentPeer.getLoggableId());
              }

              result.complete(peerResult);
              return peerResult;
            });
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return super.isRetryableError(error) || error instanceof IncompleteResultsException;
  }

  @Override
  protected void handleTaskError(final Throwable error) {
    if (getRetryCount() < getMaxRetries()) {
      LOG.atDebug()
          .setMessage("Failed to get {} blocks from peer {}, attempt {}, retrying later")
          .addArgument(headers::size)
          .addArgument(this::getAssignedPeer)
          .addArgument(this::getRetryCount)
          .log();
    } else {
      LOG.debug("Failed to get {} blocks after {} retries", headers.size(), getRetryCount());
    }
    super.handleTaskError(error);
  }
}
