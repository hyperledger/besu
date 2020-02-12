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
import java.util.OptionalLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoTransactionLogsIndexingService {
  protected static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;
  private final TransactionLogsIndexer transactionLogsIndexer;
  private OptionalLong blockAddedSubscriptionId = OptionalLong.empty();
  private OptionalLong chainReorgSubscriptionId = OptionalLong.empty();

  public AutoTransactionLogsIndexingService(
      final Blockchain blockchain, final TransactionLogsIndexer transactionLogsIndexer) {
    this.blockchain = blockchain;
    this.transactionLogsIndexer = transactionLogsIndexer;
  }

  public void start() {
    try {
      LOG.info("Starting Auto transaction logs indexing service.");
      final Path cacheDir = transactionLogsIndexer.getCacheDir();
      if (!cacheDir.toFile().exists() || !cacheDir.toFile().isDirectory()) {
        Files.createDirectory(cacheDir);
      }
      blockAddedSubscriptionId =
          OptionalLong.of(
              blockchain.observeBlockAdded(
                  (event, __) -> {
                    if (event.isNewCanonicalHead()) {
                      transactionLogsIndexer.cacheLogsBloomForBlockHeader(
                          event.getBlock().getHeader());
                    }
                  }));
      chainReorgSubscriptionId =
          OptionalLong.of(
              blockchain.observeChainReorg(
                  (header, __) -> transactionLogsIndexer.cacheLogsBloomForBlockHeader(header)));

      transactionLogsIndexer
          .getScheduler()
          .scheduleFutureTask(transactionLogsIndexer::indexAll, Duration.ofMinutes(1));
    } catch (IOException e) {
      LOG.error("Unhandled indexing exception.", e);
    }
  }

  public void stop() {
    LOG.info("Shutting down Auto transaction logs indexing service.");
    blockAddedSubscriptionId.ifPresent(blockchain::removeObserver);
    chainReorgSubscriptionId.ifPresent(blockchain::removeChainReorgObserver);
  }
}
