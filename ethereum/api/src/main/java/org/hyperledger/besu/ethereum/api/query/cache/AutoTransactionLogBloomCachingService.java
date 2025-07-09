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
package org.hyperledger.besu.ethereum.api.query.cache;

import static org.hyperledger.besu.ethereum.api.query.cache.LogBloomCacheMetadata.DEFAULT_VERSION;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTransactionLogBloomCachingService {
  private static final Logger LOG =
      LoggerFactory.getLogger(AutoTransactionLogBloomCachingService.class);
  private final Blockchain blockchain;
  private final TransactionLogBloomCacher transactionLogBloomCacher;
  private OptionalLong blockAddedSubscriptionId = OptionalLong.empty();

  public AutoTransactionLogBloomCachingService(
      final Blockchain blockchain, final TransactionLogBloomCacher transactionLogBloomCacher) {
    this.blockchain = blockchain;
    this.transactionLogBloomCacher = transactionLogBloomCacher;
  }

  public void start() {
    try {
      LOG.info("Starting auto transaction log bloom caching service.");
      final Path cacheDir = transactionLogBloomCacher.getCacheDir();
      if (!cacheDir.toFile().exists() || !cacheDir.toFile().isDirectory()) {
        Files.createDirectory(cacheDir);
      }
      final LogBloomCacheMetadata logBloomCacheMetadata =
          LogBloomCacheMetadata.lookUpFrom(cacheDir);
      if (logBloomCacheMetadata.getVersion() < DEFAULT_VERSION) {
        try (final Stream<Path> walk = Files.walk(cacheDir)) {
          walk.filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
        } catch (final Exception e) {
          LOG.error("Failed to update cache {}", e.getMessage());
        }
        new LogBloomCacheMetadata(DEFAULT_VERSION).writeToDirectory(cacheDir);
      }

      blockAddedSubscriptionId =
          OptionalLong.of(
              blockchain.observeBlockAdded(
                  event -> {
                    if (event.isNewCanonicalHead()) {
                      final BlockHeader eventBlockHeader = event.getBlock().getHeader();
                      final Optional<BlockHeader> commonAncestorBlockHeader =
                          blockchain.getBlockHeader(event.getCommonAncestorHash());
                      transactionLogBloomCacher.cacheLogsBloomForBlockHeader(
                          eventBlockHeader, commonAncestorBlockHeader, Optional.empty());
                    }
                  }));

      transactionLogBloomCacher
          .getScheduler()
          .scheduleFutureTask(
              () ->
                  // run long tasks in the computation executor
                  transactionLogBloomCacher
                      .getScheduler()
                      .scheduleComputationTask(
                          () -> {
                            transactionLogBloomCacher.cacheAll();
                            return null;
                          }),
              Duration.ofMinutes(1));
    } catch (final IOException e) {
      LOG.error("Unhandled caching exception.", e);
    }
  }

  public void stop() {
    LOG.info("Shutting down Auto transaction logs caching service.");
    blockAddedSubscriptionId.ifPresent(blockchain::removeObserver);
  }
}
