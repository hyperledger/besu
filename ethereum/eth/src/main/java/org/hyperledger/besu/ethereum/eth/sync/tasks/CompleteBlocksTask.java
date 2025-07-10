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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompleteBlocksTask extends AbstractCompleteBlocksTask<Block> {

  private static final Logger LOG = LoggerFactory.getLogger(CompleteBlocksTask.class);

  private CompleteBlocksTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, ethContext, headers, maxRetries, metricsSystem);
  }

  public static CompleteBlocksTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    return new CompleteBlocksTask(protocolSchedule, ethContext, headers, maxRetries, metricsSystem);
  }

  public static CompleteBlocksTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new CompleteBlocksTask(
        protocolSchedule, ethContext, headers, DEFAULT_RETRIES, metricsSystem);
  }

  @Override
  Block createEmptyBlock(final BlockHeader header) {
    return new Block(
        header,
        new BlockBody(
            Collections.emptyList(),
            Collections.emptyList(),
            isWithdrawalsEnabled(header)
                ? Optional.of(Collections.emptyList())
                : Optional.empty()));
  }

  @Override
  CompletableFuture<List<Block>> requestBodies(final Optional<EthPeer> assignedPeer) {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    if (incompleteHeaders.isEmpty()) {
      return completedFuture(Collections.emptyList());
    }
    LOG.atTrace()
        .setMessage("Requesting bodies to complete {} blocks, starting with {}.")
        .addArgument(incompleteHeaders.size())
        .addArgument(incompleteHeaders.getFirst().getNumber())
        .log();
    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, incompleteHeaders, metricsSystem);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run().thenApply(AbstractPeerTask.PeerTaskResult::getResult);
        });
  }

  @Override
  long getBlockNumber(final Block block) {
    return block.getHeader().getNumber();
  }
}
