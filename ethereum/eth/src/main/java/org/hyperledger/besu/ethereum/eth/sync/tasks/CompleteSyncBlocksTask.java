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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetSyncBlocksFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a set of headers, "completes" them by repeatedly requesting additional data (bodies) needed
 * to create the blocks that correspond to the supplied headers.
 */
public class CompleteSyncBlocksTask extends AbstractRetryingPeerTask<List<SyncBlock>> {
  private static final Logger LOG = LoggerFactory.getLogger(CompleteSyncBlocksTask.class);

  private static final int MIN_SIZE_INCOMPLETE_LIST = 1;
  private static final int DEFAULT_RETRIES = 5;

  private final EthContext ethContext;

  private final List<BlockHeader> headers;
  private final Map<Long, SyncBlock> blocks;
  private final MetricsSystem metricsSystem;
  private final Counter totalSyncBodiesDownloaded;
  private final ProtocolSchedule protocolSchedule;

  private CompleteSyncBlocksTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    super(ethContext, maxRetries, Collection::isEmpty, metricsSystem);
    checkArgument(!headers.isEmpty(), "Must supply a non-empty headers list");
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.protocolSchedule = protocolSchedule;

    this.headers = headers;
    this.blocks =
        headers.stream()
            .filter(BlockHeader::hasEmptyBlock)
            .collect(
                toMap(
                    BlockHeader::getNumber,
                    header ->
                        new SyncBlock(
                            header,
                            createEmptyBodyBasedOnProtocolSchedule(protocolSchedule, header))));

    totalSyncBodiesDownloaded =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "total_sync_bodies_downloaded",
            "Total number of sync bodies downloaded during sync");
  }

  @Nonnull
  private SyncBlockBody createEmptyBodyBasedOnProtocolSchedule(
      final ProtocolSchedule protocolSchedule, final BlockHeader header) {
    if (isWithdrawalsEnabled(protocolSchedule, header)) {
      return new SyncBlockBody(
          Bytes.fromHexString("0xc3c0c0c0"),
          emptyList(),
          Bytes.EMPTY,
          emptyList(),
          protocolSchedule);
    } else {
      return new SyncBlockBody(
          Bytes.fromHexString("0xc2c0c0"), emptyList(), Bytes.EMPTY, emptyList(), protocolSchedule);
    }
  }

  private boolean isWithdrawalsEnabled(
      final ProtocolSchedule protocolSchedule, final BlockHeader header) {
    return protocolSchedule.getByBlockHeader(header).getWithdrawalsProcessor().isPresent();
  }

  public static CompleteSyncBlocksTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new CompleteSyncBlocksTask(
        protocolSchedule, ethContext, headers, DEFAULT_RETRIES, metricsSystem);
  }

  @Override
  protected CompletableFuture<List<SyncBlock>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    return requestBodies(assignedPeer).thenCompose(this::processBodiesResult);
  }

  private CompletableFuture<List<SyncBlock>> requestBodies(final Optional<EthPeer> assignedPeer) {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    if (incompleteHeaders.isEmpty()) {
      return completedFuture(emptyList());
    }
    LOG.info(
        "Requesting bodies to complete {} blocks, starting with {}.",
        incompleteHeaders.size(),
        incompleteHeaders.getFirst().getNumber());
    return executeSubTask(
        () -> {
          final GetSyncBlocksFromPeerTask task =
              GetSyncBlocksFromPeerTask.forHeaders(
                  ethContext, incompleteHeaders, metricsSystem, protocolSchedule);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run().thenApply(PeerTaskResult::getResult);
        });
  }

  private CompletableFuture<List<SyncBlock>> processBodiesResult(
      final List<SyncBlock> blocksResult) {
    blocksResult.forEach(
        (syncBlockBody) -> blocks.put(syncBlockBody.getHeader().getNumber(), syncBlockBody));

    if (incompleteHeaders().isEmpty()) {
      result.complete(headers.stream().map(h -> blocks.get(h.getNumber())).toList());
      totalSyncBodiesDownloaded.inc(headers.size());
    }

    return completedFuture(blocksResult);
  }

  private List<BlockHeader> incompleteHeaders() {
    final List<BlockHeader> collectedHeaders =
        headers.stream()
            .filter(h -> blocks.get(h.getNumber()) == null)
            .collect(Collectors.toList());
    if (!collectedHeaders.isEmpty() && getRetryCount() > 1) {
      final int subSize = (int) Math.ceil((double) collectedHeaders.size() / getRetryCount());
      if (getRetryCount() > getMaxRetries()) {
        return collectedHeaders.subList(0, MIN_SIZE_INCOMPLETE_LIST);
      } else {
        return collectedHeaders.subList(0, subSize);
      }
    }

    return collectedHeaders;
  }
}
