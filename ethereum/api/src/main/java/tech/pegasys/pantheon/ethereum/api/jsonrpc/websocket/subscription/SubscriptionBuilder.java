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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscription;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.logs.LogsSubscription;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.SubscribeRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.syncing.SyncingSubscription;

import java.util.Optional;
import java.util.function.Function;

public class SubscriptionBuilder {

  public Subscription build(
      final long subscriptionId, final String connectionId, final SubscribeRequest request) {
    final SubscriptionType subscriptionType = request.getSubscriptionType();
    switch (subscriptionType) {
      case NEW_BLOCK_HEADERS:
        {
          return new NewBlockHeadersSubscription(
              subscriptionId, connectionId, request.getIncludeTransaction());
        }
      case LOGS:
        {
          return new LogsSubscription(
              subscriptionId,
              connectionId,
              Optional.ofNullable(request.getFilterParameter())
                  .orElseThrow(IllegalArgumentException::new));
        }
      case SYNCING:
        {
          return new SyncingSubscription(subscriptionId, connectionId, subscriptionType);
        }
      case NEW_PENDING_TRANSACTIONS:
      default:
        return new Subscription(
            subscriptionId, connectionId, subscriptionType, request.getIncludeTransaction());
    }
  }

  @SuppressWarnings("unchecked")
  public <T> Function<Subscription, T> mapToSubscriptionClass(final Class<T> clazz) {
    return subscription -> {
      if (clazz.isAssignableFrom(subscription.getClass())) {
        return (T) subscription;
      } else {
        final String msg =
            String.format(
                "%s instance can't be mapped to type %s",
                subscription.getClass().getSimpleName(), clazz.getSimpleName());
        throw new IllegalArgumentException(msg);
      }
    };
  }
}
