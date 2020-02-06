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

import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;

import java.time.Duration;
import java.util.OptionalLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoTransactionLogsIndexingService {
  protected static final Logger LOG = LogManager.getLogger();
  private final BlockBroadcaster blockBroadcaster;
  private final TransactionLogsIndexer transactionLogsIndexer;
  private OptionalLong subscriptionId = OptionalLong.empty();

  public AutoTransactionLogsIndexingService(
      final BlockBroadcaster blockBroadcaster,
      final TransactionLogsIndexer transactionLogsIndexer) {
    this.blockBroadcaster = blockBroadcaster;
    this.transactionLogsIndexer = transactionLogsIndexer;
  }

  public void start() {
    LOG.info("Starting Auto transaction logs indexing service.");
    subscriptionId =
        OptionalLong.of(
            blockBroadcaster.subscribePropagateNewBlocks(
                (block, __) ->
                    transactionLogsIndexer.cacheLogsBloomForBlockHeader(block.getHeader())));
    transactionLogsIndexer
        .getScheduler()
        .scheduleFutureTask(transactionLogsIndexer::indexAll, Duration.ofMinutes(1));
  }

  public void stop() {
    LOG.info("Shutting down Auto transaction logs indexing service.");
    subscriptionId.ifPresent(blockBroadcaster::unsubscribePropagateNewBlocks);
  }
}
