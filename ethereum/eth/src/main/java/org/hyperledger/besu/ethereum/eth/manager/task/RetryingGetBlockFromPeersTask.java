/*
 * Copyright contributors to Hyperledger Besu
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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetBlockFromPeersTask
    extends AbstractRetryingSwitchingPeerTask<AbstractPeerTask.PeerTaskResult<Block>> {

  private static final Logger LOG = LoggerFactory.getLogger(RetryingGetBlockFromPeersTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final Optional<Hash> maybeBlockHash;
  private final long blockNumber;

  protected RetryingGetBlockFromPeersTask(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final Optional<Hash> maybeBlockHash,
      final long blockNumber) {
    super(ethContext, metricsSystem, Objects::isNull, maxRetries);
    this.protocolSchedule = protocolSchedule;
    this.maybeBlockHash = maybeBlockHash;
    this.blockNumber = blockNumber;
  }

  public static RetryingGetBlockFromPeersTask create(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final Optional<Hash> maybeHash,
      final long blockNumber) {
    return new RetryingGetBlockFromPeersTask(
        ethContext, protocolSchedule, metricsSystem, maxRetries, maybeHash, blockNumber);
  }

  @Override
  protected CompletableFuture<PeerTaskResult<Block>> executeTaskOnCurrentPeer(
      final EthPeer currentPeer) {
    final GetBlockFromPeerTask getBlockTask =
        GetBlockFromPeerTask.create(
            protocolSchedule, getEthContext(), maybeBlockHash, blockNumber, getMetricsSystem());
    getBlockTask.assignPeer(currentPeer);

    return executeSubTask(getBlockTask::run)
        .thenApply(
            peerResult -> {
              debugLambda(
                  LOG,
                  "Got block {} from peer {}, attempt {}",
                  peerResult.getResult()::toLogString,
                  peerResult.getPeer()::toString,
                  this::getRetryCount);
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
      debugLambda(
          LOG,
          "Failed to get block {} from peer {}, attempt {}, retrying later",
          this::logBlockNumberMaybeHash,
          this::getAssignedPeer,
          this::getRetryCount);
    } else {
      LOG.warn(
          "Failed to get block {} after {} retries", logBlockNumberMaybeHash(), getRetryCount());
    }
    super.handleTaskError(error);
  }

  private String logBlockNumberMaybeHash() {
    return blockNumber + maybeBlockHash.map(h -> " (" + h.toHexString() + ")").orElse("");
  }
}
