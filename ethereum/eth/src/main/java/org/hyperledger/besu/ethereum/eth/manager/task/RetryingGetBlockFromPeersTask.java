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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetBlockFromPeersTask
    extends AbstractRetryingPeerTask<AbstractPeerTask.PeerTaskResult<Block>> {

  private static final int DEFAULT_MAX_RETRIES = 5;

  private static final Logger LOG = LoggerFactory.getLogger(RetryingGetBlockFromPeersTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final Optional<Hash> blockHash;
  private final long blockNumber;

  public RetryingGetBlockFromPeersTask(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final Optional<Hash> blockHash,
      final long blockNumber,
      final int maxRetries) {
    super(ethContext, maxRetries, Objects::isNull, metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
  }

  public static RetryingGetBlockFromPeersTask create(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final Optional<Hash> hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    return new RetryingGetBlockFromPeersTask(
        ethContext, protocolSchedule, metricsSystem, hash, blockNumber, DEFAULT_MAX_RETRIES);
  }

  @Override
  protected CompletableFuture<AbstractPeerTask.PeerTaskResult<Block>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    final GetBlockFromPeersTask getHeadersTask =
        GetBlockFromPeersTask.create(
            getEthContext().getEthPeers().streamAvailablePeers().collect(Collectors.toList()),
            protocolSchedule,
            getEthContext(),
            blockHash,
            blockNumber,
            getMetricsSystem());
    return executeSubTask(getHeadersTask::run)
        .thenApply(
            peerResult -> {
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
      LOG.info(
          "Failed to get block with hash {} and number {} retrying later", blockHash, blockNumber);
    }
    super.handleTaskError(error);
  }
}
