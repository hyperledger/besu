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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoTransactionLogsIndexingService {
  protected static final Logger LOG = LogManager.getLogger();

  private final TransactionLogsIndexer transactionLogsIndexer;
  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor();
  private TransactionLogsIndexer.IndexingStatus lastIndexingStatus;

  public AutoTransactionLogsIndexingService(final TransactionLogsIndexer transactionLogsIndexer) {
    this.transactionLogsIndexer = transactionLogsIndexer;
  }

  public void start() {
    LOG.info("Starting Auto transaction logs indexing service.");
    executorService.scheduleAtFixedRate(this::doIndex, 0, 10, TimeUnit.SECONDS);
  }

  public void doIndex() {
    LOG.info("Starting auto scheduled indexing.");
    long startBlock = 0, stopBlock = Long.MAX_VALUE;
    if (lastIndexingStatus != null) {
      startBlock = lastIndexingStatus.currentBlock;
    }
    LOG.info("Calling log bloom cache with start = {} and stop = {}", startBlock, stopBlock);
    lastIndexingStatus = transactionLogsIndexer.generateLogBloomCache(startBlock, stopBlock);
    LOG.info("generateLogBloomCache completed with status: {}", lastIndexingStatus.toString());
  }

  public void stop() {
    LOG.info("Shutting down Auto transaction logs indexing service.");
  }
}
