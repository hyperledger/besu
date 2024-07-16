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
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class RetryingGetBytecodeFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<Map<Bytes32, Bytes>> {

  public static final int MAX_RETRIES = 4;

  private final EthContext ethContext;
  private final List<Bytes32> codeHashes;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetBytecodeFromPeerTask(
      final EthContext ethContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem, Map::isEmpty, MAX_RETRIES);
    this.ethContext = ethContext;
    this.codeHashes = codeHashes;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static EthTask<Map<Bytes32, Bytes>> forByteCode(
      final EthContext ethContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetBytecodeFromPeerTask(ethContext, codeHashes, blockHeader, metricsSystem);
  }

  @Override
  protected CompletableFuture<Map<Bytes32, Bytes>> executeTaskOnCurrentPeer(final EthPeer peer) {
    final GetBytecodeFromPeerTask task =
        GetBytecodeFromPeerTask.forBytecode(ethContext, codeHashes, blockHeader, metricsSystem);
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
