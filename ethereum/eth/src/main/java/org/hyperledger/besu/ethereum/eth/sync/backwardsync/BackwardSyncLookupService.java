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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;

@ThreadSafe
public class BackwardSyncLookupService {
  private static final Logger LOG = getLogger(BackwardSyncLookupService.class);
  private static final int MAX_RETRIES = 100;

  @GuardedBy("this")
  private final Queue<Hash> hashes = new ArrayDeque<>();

  @GuardedBy("this")
  boolean running = false;

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private List<Block> results = new ArrayList<>();

  public BackwardSyncLookupService(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<List<Block>> lookup(final Hash newBlockhash) {
    synchronized (this) {
      hashes.add(newBlockhash);
      if (running) {
        LOG.info(
            "some other future is already running and will process our hash {} when time comes...",
            newBlockhash.toHexString());
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      running = true;
    }
    return findBlocksWithRetries();
  }

  private CompletableFuture<List<Block>> findBlocksWithRetries() {

    CompletableFuture<List<Block>> f = tryToFindBlocks();
    for (int i = 0; i < MAX_RETRIES; i++) {
      f =
          f.thenApply(CompletableFuture::completedFuture)
              .exceptionally(
                  ex -> {
                    synchronized (this) {
                      if (!results.isEmpty()) {
                        List<Block> copy = new ArrayList<>(results);
                        results = new ArrayList<>();
                        return CompletableFuture.completedFuture(copy);
                      }
                    }
                    LOG.error(
                        "Failed to fetch blocks because {} Current peers: {}.  Waiting for few seconds ...",
                        ex.getMessage(), ethContext.getEthPeers().peerCount());
                    return ethContext.getScheduler().scheduleFutureTask(this::tryToFindBlocks, Duration.ofSeconds(5));
                  })
              .thenCompose(Function.identity());
    }
    return f.thenApply(this::rememberResults).thenCompose(this::possibleNextHash);
  }

  private CompletableFuture<List<Block>> possibleNextHash(final List<Block> blocks) {
    synchronized (this) {
      hashes.poll();
      if (hashes.isEmpty()) {
        results = new ArrayList<>();
        running = false;
        return CompletableFuture.completedFuture(blocks);
      }
    }
    return findBlocksWithRetries();
  }

  private List<Block> rememberResults(final List<Block> blocks) {
    this.results.addAll(blocks);
    return results;
  }

  private synchronized Hash getNextHash() {
    return hashes.peek();
  }

  private CompletableFuture<List<Block>> tryToFindBlocks() {
    return CompletableFuture.supplyAsync(
            () ->
                GetHeadersFromPeerByHashTask.forSingleHash(
                        protocolSchedule, ethContext, getNextHash(), 0L, metricsSystem)
                    .run())
        .thenCompose(f -> f)
        .thenCompose(
            headers ->
                GetBodiesFromPeerTask.forHeaders(
                        protocolSchedule, ethContext, headers.getResult(), metricsSystem)
                    .run())
        .thenApply(AbstractPeerTask.PeerTaskResult::getResult);
  }
}
