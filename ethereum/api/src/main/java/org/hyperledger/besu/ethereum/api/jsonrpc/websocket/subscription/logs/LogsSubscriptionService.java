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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.Optional;
import java.util.function.Consumer;

public class LogsSubscriptionService implements Consumer<LogWithMetadata> {

  private final SubscriptionManager subscriptionManager;
  private final Optional<PrivacyQueries> privacyQueries;

  public LogsSubscriptionService(
      final SubscriptionManager subscriptionManager,
      final Optional<PrivacyQueries> privacyQueries) {
    this.subscriptionManager = subscriptionManager;
    this.privacyQueries = privacyQueries;
  }

  @Override
  public void accept(final LogWithMetadata logWithMetadata) {
    subscriptionManager.subscriptionsOfType(SubscriptionType.LOGS, LogsSubscription.class).stream()
        .filter(
            logsSubscription -> {
              final FilterParameter filterParameter = logsSubscription.getFilterParameter();
              final long blockNumber = logWithMetadata.getBlockNumber();
              return filterParameter
                          .getFromBlock()
                          .getNumber()
                          .orElse(BlockHeader.GENESIS_BLOCK_NUMBER)
                      <= blockNumber
                  && filterParameter.getToBlock().getNumber().orElse(Long.MAX_VALUE) >= blockNumber
                  && filterParameter.getLogsQuery().matches(logWithMetadata);
            })
        .forEach(logsSubscription -> sendLogToSubscription(logWithMetadata, logsSubscription));
  }

  public void checkPrivateLogs(final BlockAddedEvent event) {
    privacyQueries.ifPresent(
        pq ->
            subscriptionManager
                .subscriptionsOfType(SubscriptionType.LOGS, LogsSubscription.class)
                .stream()
                .filter(PrivateLogsSubscription.class::isInstance)
                .map(PrivateLogsSubscription.class::cast)
                .forEach(queryPrivateEventForSubscription(pq, event)));
  }

  private Consumer<PrivateLogsSubscription> queryPrivateEventForSubscription(
      final PrivacyQueries privacyQueries, final BlockAddedEvent event) {
    return subscription -> {
      final String privacyGroupId = subscription.getPrivacyGroupId();
      final LogsQuery logsQuery = subscription.getFilterParameter().getLogsQuery();

      privacyQueries
          .matchingLogs(privacyGroupId, event.getBlock().getHash(), logsQuery)
          .forEach(logWithMetadata -> sendLogToSubscription(logWithMetadata, subscription));
    };
  }

  private void sendLogToSubscription(
      final LogWithMetadata logWithMetadata, final LogsSubscription subscription) {
    subscriptionManager.sendMessage(
        subscription.getSubscriptionId(), new LogResult(logWithMetadata));
  }
}
