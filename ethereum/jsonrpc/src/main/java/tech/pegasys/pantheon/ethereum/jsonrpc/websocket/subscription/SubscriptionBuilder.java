package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription;

import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.logs.LogsSubscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscribeRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing.SyncingSubscription;

import java.util.Optional;
import java.util.function.Function;

public class SubscriptionBuilder {

  public Subscription build(final long id, final SubscribeRequest request) {
    final SubscriptionType subscriptionType = request.getSubscriptionType();
    switch (subscriptionType) {
      case NEW_BLOCK_HEADERS:
        {
          return new NewBlockHeadersSubscription(id, request.getIncludeTransaction());
        }
      case LOGS:
        {
          return new LogsSubscription(
              id,
              Optional.ofNullable(request.getFilterParameter())
                  .orElseThrow(IllegalArgumentException::new));
        }
      case SYNCING:
        {
          return new SyncingSubscription(id, subscriptionType);
        }
      case NEW_PENDING_TRANSACTIONS:
      default:
        return new Subscription(id, subscriptionType);
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
