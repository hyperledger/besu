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
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodes;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodes;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.TrieNodeDataRequest;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RetryingGetTrieNodeFromPeerTask extends AbstractRetryingPeerTask<TrieNodes> {

  private final EthContext ethContext;
  private final GetTrieNodes message;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetTrieNodeFromPeerTask(
      final EthContext ethContext,
      final GetTrieNodes message,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, 3, data -> false, metricsSystem);
    this.ethContext = ethContext;
    this.message = message;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static EthTask<TrieNodes> forTrieNodes(
      final EthContext ethContext,
      final SnapDataRequest request,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetTrieNodeFromPeerTask(
        ethContext,
        TrieNodeDataRequest.create(blockHeader.getStateRoot(), (TrieNodeDataRequest) request),
        blockHeader,
        metricsSystem);
  }

  @Override
  protected CompletableFuture<TrieNodes> executePeerTask(final Optional<EthPeer> assignedPeer) {
    final GetTrieNodeFromPeerTask task =
        GetTrieNodeFromPeerTask.forTrieNodes(ethContext, message, blockHeader, metricsSystem);
    assignedPeer.ifPresent(task::assignPeer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }
}
