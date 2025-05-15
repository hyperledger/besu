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
package org.hyperledger.besu.preload;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.PreloadTask;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.Preloader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreloadService implements Preloader {

  private static final Logger LOG = LoggerFactory.getLogger(PreloadService.class);

  private BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader;

  protected final ExecutorService servicesExecutor;
  private final Collection<CompletableFuture<?>> pendingFutures = new ConcurrentLinkedDeque<>();

  public PreloadService(final ExecutorService servicesExecutor) {
    LOG.info("Creating PreloadService " + this);
    this.servicesExecutor = servicesExecutor;
  }

  @Override
  public synchronized void enqueueRequest(final PreloadTask request) {
    final CompletableFuture<Void> syncFuture = CompletableFuture.runAsync(() -> bonsaiCachedMerkleTrieLoader.processPreloadTask(request), servicesExecutor);
    pendingFutures.add(syncFuture);
    syncFuture.whenComplete((r, t) -> pendingFutures.remove(syncFuture));
  }

  @Override
  public synchronized void clearQueue() {
    pendingFutures.forEach(future -> future.cancel(true));
  }
}
