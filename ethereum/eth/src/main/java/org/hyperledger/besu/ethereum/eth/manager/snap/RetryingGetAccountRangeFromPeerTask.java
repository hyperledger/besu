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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingSwitchingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes32;

public class RetryingGetAccountRangeFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<AccountRangeMessage.AccountRangeData> {

  public static final int MAX_RETRIES = 4;

  private final EthContext ethContext;
  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetAccountRangeFromPeerTask(
      final EthContext ethContext,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(
        ethContext,
        metricsSystem,
        data -> data.accounts().isEmpty() && data.proofs().isEmpty(),
        MAX_RETRIES);
    this.ethContext = ethContext;
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static EthTask<AccountRangeMessage.AccountRangeData> forAccountRange(
      final EthContext ethContext,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetAccountRangeFromPeerTask(
        ethContext, startKeyHash, endKeyHash, blockHeader, metricsSystem);
  }

  @Override
  protected CompletableFuture<AccountRangeMessage.AccountRangeData> executeTaskOnCurrentPeer(
      final EthPeer peer) {
    final GetAccountRangeFromPeerTask task =
        GetAccountRangeFromPeerTask.forAccountRange(
            ethContext, startKeyHash, endKeyHash, blockHeader, metricsSystem);
    task.assignPeer(peer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }

  @Override
  protected boolean isSuitablePeer(final EthPeer peer) {
    return peer.isServingSnap();
  }
}
