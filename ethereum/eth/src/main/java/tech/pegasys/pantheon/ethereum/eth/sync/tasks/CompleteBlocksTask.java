/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractRetryingPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Given a set of headers, "completes" them by repeatedly requesting additional data (bodies) needed
 * to create the blocks that correspond to the supplied headers.
 *
 * @param <C> the consensus algorithm context
 */
public class CompleteBlocksTask<C> extends AbstractRetryingPeerTask<List<Block>> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_RETRIES = 3;

  private final EthContext ethContext;
  private final ProtocolSchedule<C> protocolSchedule;

  private final List<BlockHeader> headers;
  private final Map<Long, Block> blocks;
  private final MetricsSystem metricsSystem;

  private CompleteBlocksTask(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    super(ethContext, maxRetries, Collection::isEmpty, metricsSystem);
    checkArgument(headers.size() > 0, "Must supply a non-empty headers list");
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;

    this.headers = headers;
    this.blocks =
        headers.stream()
            .filter(this::hasEmptyBody)
            .collect(toMap(BlockHeader::getNumber, header -> new Block(header, BlockBody.empty())));
  }

  private boolean hasEmptyBody(final BlockHeader header) {
    return header.getOmmersHash().equals(Hash.EMPTY_LIST_HASH)
        && header.getTransactionsRoot().equals(Hash.EMPTY_TRIE_HASH);
  }

  public static <C> CompleteBlocksTask<C> forHeaders(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    return new CompleteBlocksTask<>(
        protocolSchedule, ethContext, headers, maxRetries, metricsSystem);
  }

  public static <C> CompleteBlocksTask<C> forHeaders(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new CompleteBlocksTask<>(
        protocolSchedule, ethContext, headers, DEFAULT_RETRIES, metricsSystem);
  }

  @Override
  protected CompletableFuture<List<Block>> executePeerTask(final Optional<EthPeer> assignedPeer) {
    return requestBodies(assignedPeer).thenCompose(this::processBodiesResult);
  }

  private CompletableFuture<List<Block>> requestBodies(final Optional<EthPeer> assignedPeer) {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    if (incompleteHeaders.isEmpty()) {
      return completedFuture(emptyList());
    }
    LOG.debug(
        "Requesting bodies to complete {} blocks, starting with {}.",
        incompleteHeaders.size(),
        incompleteHeaders.get(0).getNumber());
    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask<C> task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, incompleteHeaders, metricsSystem);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run().thenApply(PeerTaskResult::getResult);
        });
  }

  private CompletableFuture<List<Block>> processBodiesResult(final List<Block> blocksResult) {
    blocksResult.forEach((block) -> blocks.put(block.getHeader().getNumber(), block));

    if (incompleteHeaders().isEmpty()) {
      result
          .get()
          .complete(
              headers.stream().map(h -> blocks.get(h.getNumber())).collect(Collectors.toList()));
    }

    return completedFuture(blocksResult);
  }

  private List<BlockHeader> incompleteHeaders() {
    return headers.stream()
        .filter(h -> blocks.get(h.getNumber()) == null)
        .collect(Collectors.toList());
  }
}
