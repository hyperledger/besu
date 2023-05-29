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
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;

public class RetryingGetNodeDataFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<Map<Hash, Bytes>> {

  private final EthContext ethContext;
  private final Set<Hash> hashes;
  private final long pivotBlockNumber;
  private final MetricsSystem metricsSystem;

  private RetryingGetNodeDataFromPeerTask(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final long pivotBlockNumber,
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    super(ethContext, metricsSystem, maxRetries);
    this.ethContext = ethContext;
    this.hashes = new HashSet<>(hashes);
    this.pivotBlockNumber = pivotBlockNumber;
    this.metricsSystem = metricsSystem;
  }

  public static RetryingGetNodeDataFromPeerTask forHashes(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final long pivotBlockNumber,
      final MetricsSystem metricsSystem,
      final int maxRetries) {
    return new RetryingGetNodeDataFromPeerTask(
        ethContext, hashes, pivotBlockNumber, metricsSystem, maxRetries);
  }

  @Override
  protected CompletableFuture<Map<Hash, Bytes>> executeTaskOnCurrentPeer(final EthPeer peer) {
    final GetNodeDataFromPeerTask task =
        GetNodeDataFromPeerTask.forHashes(ethContext, hashes, pivotBlockNumber, metricsSystem);
    task.assignPeer(peer);
    return executeSubTask(task::run).thenApply(PeerTaskResult::getResult);
  }

  @Override
  protected boolean emptyResult(final Map<Hash, Bytes> peerResult) {
    return false;
  }

  @Override
  protected boolean successfulResult(final Map<Hash, Bytes> peerResult) {
    return !emptyResult(peerResult);
  }
}
