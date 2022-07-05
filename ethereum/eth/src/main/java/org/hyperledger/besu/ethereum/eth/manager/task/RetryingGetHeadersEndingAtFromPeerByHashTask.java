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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;

public class RetryingGetHeadersEndingAtFromPeerByHashTask
    extends AbstractRetryingPeerTask<List<BlockHeader>> {

  private final Hash referenceHash;
  private final ProtocolSchedule protocolSchedule;
  private final int count;

  @VisibleForTesting
  RetryingGetHeadersEndingAtFromPeerByHashTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final int count,
      final MetricsSystem metricsSystem) {
    super(ethContext, 3, List::isEmpty, metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.count = count;
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static RetryingGetHeadersEndingAtFromPeerByHashTask endingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final int count,
      final MetricsSystem metricsSystem) {
    return new RetryingGetHeadersEndingAtFromPeerByHashTask(
        protocolSchedule, ethContext, referenceHash, count, metricsSystem);
  }

  @Override
  protected CompletableFuture<List<BlockHeader>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    final AbstractGetHeadersFromPeerTask task =
        GetHeadersFromPeerByHashTask.endingAtHash(
            protocolSchedule, getEthContext(), referenceHash, count, getMetricsSystem());
    assignedPeer.ifPresent(task::assignPeer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }
}
