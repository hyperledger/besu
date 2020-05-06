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
package org.hyperledger.besu.ethereum.api.query;

import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoTransactionLogBloomCachingService {
  private static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;
  private final TransactionLogBloomCacher transactionLogBloomCacher;
  private OptionalLong blockAddedSubscriptionId = OptionalLong.empty();
  private OptionalLong chainReorgSubscriptionId = OptionalLong.empty();

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
      blockAddedSubscriptionId =
          OptionalLong.of(
              blockchain.observeBlockAdded(
                  event -> {
                    if (event.isNewCanonicalHead()) {
                      transactionLogBloomCacher.cacheLogsBloomForBlockHeader(
                          event.getBlock().getHeader(), Optional.empty(), true);
                    }
                  }));
      chainReorgSubscriptionId =
          OptionalLong.of(
              blockchain.observeChainReorg(
                  (blockWithReceipts, __) ->
                      transactionLogBloomCacher.cacheLogsBloomForBlockHeader(
                          blockWithReceipts.getHeader(), Optional.empty(), true)));

      transactionLogBloomCacher
          .getScheduler()
          .scheduleFutureTask(transactionLogBloomCacher::cacheAll, Duration.ofMinutes(1));
    } catch (IOException e) {
      LOG.error("Unhandled caching exception.", e);
    }
  }

  public void stop() {
    LOG.info("Shutting down Auto transaction logs caching service.");
    blockAddedSubscriptionId.ifPresent(blockchain::removeObserver);
    chainReorgSubscriptionId.ifPresent(blockchain::removeChainReorgObserver);
  }
}
