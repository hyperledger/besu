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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDataRequest;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RetryingGetAccountRangeFromPeerTask
    extends AbstractRetryingPeerTask<AccountRangeMessage> {

  private final EthContext ethContext;
  private final GetAccountRangeMessage message;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetAccountRangeFromPeerTask(
      final EthContext ethContext,
      final GetAccountRangeMessage message,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, 3, data -> false, metricsSystem);
    this.ethContext = ethContext;
    this.message = message;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static EthTask<AccountRangeMessage> forAccountRange(
      final EthContext ethContext,
      final SnapDataRequest request,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetAccountRangeFromPeerTask(
        ethContext,
        ((AccountRangeDataRequest) request).getAccountRangeMessage(),
        blockHeader,
        metricsSystem);
  }

  @Override
  protected CompletableFuture<AccountRangeMessage> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    final GetAccountRangeFromPeerTask task =
        GetAccountRangeFromPeerTask.forAccountRange(
            ethContext, message, blockHeader, metricsSystem);
    assignedPeer.ifPresent(task::assignPeer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }
}
