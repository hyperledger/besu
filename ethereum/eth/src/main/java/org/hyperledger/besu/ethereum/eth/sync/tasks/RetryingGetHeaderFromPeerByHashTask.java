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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractGetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingSwitchingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetHeaderFromPeerByHashTask
    extends AbstractRetryingSwitchingPeerTask<List<BlockHeader>> {
  private static final Logger LOG =
      LoggerFactory.getLogger(RetryingGetHeaderFromPeerByHashTask.class);
  private final Hash referenceHash;
  private final ProtocolSchedule protocolSchedule;
  private final long minimumRequiredBlockNumber;

  @VisibleForTesting
  RetryingGetHeaderFromPeerByHashTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    super(ethContext, metricsSystem, List::isEmpty, maxRetries);
    this.protocolSchedule = protocolSchedule;
    this.minimumRequiredBlockNumber = minimumRequiredBlockNumber;
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static RetryingGetHeaderFromPeerByHashTask byHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final MetricsSystem metricsSystem) {
    return new RetryingGetHeaderFromPeerByHashTask(
        protocolSchedule,
        ethContext,
        referenceHash,
        minimumRequiredBlockNumber,
        metricsSystem,
        ethContext.getEthPeers().peerCount());
  }

  @Override
  protected CompletableFuture<List<BlockHeader>> executeTaskOnCurrentPeer(final EthPeer peer) {
    final AbstractGetHeadersFromPeerTask task =
        GetHeadersFromPeerByHashTask.forSingleHash(
            protocolSchedule,
            getEthContext(),
            referenceHash,
            minimumRequiredBlockNumber,
            getMetricsSystem());
    task.assignPeer(peer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              LOG.debug(
                  "Get block header by hash {} from peer {} has result {}",
                  referenceHash,
                  peer,
                  peerResult.getResult());
              if (peerResult.getResult().isEmpty()) {
                throw new IncompleteResultsException(
                    "No block header for hash "
                        + referenceHash
                        + " returned by peer "
                        + peer.getLoggableId());
              }
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return super.isRetryableError(error) || error instanceof IncompleteResultsException;
  }

  public CompletableFuture<BlockHeader> getHeader() {
    return run().thenApply(singletonList -> singletonList.get(0));
  }
}
