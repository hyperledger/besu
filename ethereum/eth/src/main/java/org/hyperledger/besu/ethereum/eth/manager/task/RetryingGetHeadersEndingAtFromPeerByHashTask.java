/*
 * Copyright Hyperledger Besu Contributors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetHeadersEndingAtFromPeerByHashTask
    extends AbstractRetryingSwitchingPeerTask<List<BlockHeader>> {
  private static final Logger LOG =
      LoggerFactory.getLogger(RetryingGetHeadersEndingAtFromPeerByHashTask.class);

  private final Hash referenceHash;
  private final ProtocolSchedule protocolSchedule;
  private final long minimumRequiredBlockNumber;
  private final int count;

  @VisibleForTesting
  RetryingGetHeadersEndingAtFromPeerByHashTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final int count,
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    super(ethContext, metricsSystem, maxRetries);
    this.protocolSchedule = protocolSchedule;
    this.minimumRequiredBlockNumber = minimumRequiredBlockNumber;
    this.count = count;
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static RetryingGetHeadersEndingAtFromPeerByHashTask endingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final int count,
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    return new RetryingGetHeadersEndingAtFromPeerByHashTask(
        protocolSchedule,
        ethContext,
        referenceHash,
        minimumRequiredBlockNumber,
        count,
        metricsSystem,
        maxRetries);
  }

  @Override
  protected CompletableFuture<List<BlockHeader>> executeTaskOnCurrentPeer(
      final EthPeer currentPeer) {
    final AbstractGetHeadersFromPeerTask task =
        GetHeadersFromPeerByHashTask.endingAtHash(
            protocolSchedule,
            getEthContext(),
            referenceHash,
            minimumRequiredBlockNumber,
            count,
            getMetricsSystem());
    task.assignPeer(currentPeer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              final var res = peerResult.getResult();
              LOG.debug(
                  "Get {} block headers by hash {} from peer {} has result {}",
                  count,
                  referenceHash,
                  currentPeer,
                  res);
              return res;
            });
  }

  @Override
  protected boolean emptyResult(final List<BlockHeader> peerResult) {
    return peerResult.isEmpty();
  }

  @Override
  protected boolean successfulResult(final List<BlockHeader> peerResult) {
    return !emptyResult(peerResult);
  }
}
