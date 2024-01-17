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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingSwitchingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes32;

public class RetryingGetAccountRangeFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<AccountRangeMessage.AccountRangeData> {

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
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    super(ethContext, metricsSystem, maxRetries);
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
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    return new RetryingGetAccountRangeFromPeerTask(
        ethContext, startKeyHash, endKeyHash, blockHeader, metricsSystem, maxRetries);
  }

  @Override
  protected CompletableFuture<AccountRangeMessage.AccountRangeData> executeTaskOnCurrentPeer(
      final EthPeer assignedPeer) {
    final GetAccountRangeFromPeerTask task =
        GetAccountRangeFromPeerTask.forAccountRange(
            ethContext, startKeyHash, endKeyHash, blockHeader, metricsSystem);
    task.assignPeer(assignedPeer);
    return executeSubTask(task::run).thenApply(PeerTaskResult::getResult);
  }

  @Override
  protected boolean emptyResult(final AccountRangeMessage.AccountRangeData data) {
    return data.accounts().isEmpty() && data.proofs().isEmpty();
  }

  @Override
  protected boolean reportUselessIfEmptyResponse() {
    return false;
  }

  @Override
  protected boolean successfulResult(final AccountRangeMessage.AccountRangeData peerResult) {
    return !emptyResult(peerResult);
  }

  @Override
  protected Predicate<EthPeer> getPeerFilter() {
    return (peer) -> peer.getConnection().getAgreedCapabilities().stream().anyMatch( (c) -> c.getName().equals(SnapProtocol.NAME));
  }
}
