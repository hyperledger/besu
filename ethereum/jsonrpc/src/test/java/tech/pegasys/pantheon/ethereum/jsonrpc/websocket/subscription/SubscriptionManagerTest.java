package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscribeRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.UnsubscribeRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing.SyncingSubscription;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SubscriptionManagerTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private SubscriptionManager subscriptionManager;
  private final String CONNECTION_ID = "test-connection-id";

  @Before
  public void before() {
    subscriptionManager = new SubscriptionManager();
  }

  @Test
  public void subscribeShouldCreateSubscription() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);

    final Long subscriptionId = subscriptionManager.subscribe(subscribeRequest);

    final SyncingSubscription expectedSubscription =
        new SyncingSubscription(subscriptionId, subscribeRequest.getSubscriptionType());
    final Subscription createdSubscription =
        subscriptionManager.subscriptions().get(subscriptionId);

    assertThat(subscriptionId).isEqualTo(1L);
    assertThat(createdSubscription).isEqualTo(expectedSubscription);
  }

  @Test
  public void unsubscribeExistingSubscriptionShouldDestroySubscription() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);
    final Long subscriptionId = subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.subscriptions().get(subscriptionId)).isNotNull();

    final UnsubscribeRequest unsubscribeRequest =
        new UnsubscribeRequest(subscriptionId, CONNECTION_ID);
    final boolean unsubscribed = subscriptionManager.unsubscribe(unsubscribeRequest);

    assertThat(unsubscribed).isTrue();
    assertThat(subscriptionManager.subscriptions().get(subscriptionId)).isNull();
  }

  @Test
  public void unsubscribeAbsentSubscriptionShouldThrowSubscriptionNotFoundException() {
    final UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);

    thrown.expect(
        both(hasMessage(equalTo("Subscription not found (id=1)")))
            .and(instanceOf(SubscriptionNotFoundException.class)));

    subscriptionManager.unsubscribe(unsubscribeRequest);
  }

  @Test
  public void shouldAddSubscriptionToNewConnection() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);

    subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().containsKey(CONNECTION_ID))
        .isTrue();
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(1);
  }

  @Test
  public void shouldAddSubscriptionToExistingConnection() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);

    subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().containsKey(CONNECTION_ID))
        .isTrue();
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(1);

    subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(2);
  }

  @Test
  public void shouldRemoveSubscriptionFromExistingConnection() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);

    final Long subscriptionId1 = subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().containsKey(CONNECTION_ID))
        .isTrue();
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(1);

    final Long subscriptionId2 = subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(2);

    final UnsubscribeRequest unsubscribeRequest =
        new UnsubscribeRequest(subscriptionId1, CONNECTION_ID);
    subscriptionManager.unsubscribe(unsubscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).get(0))
        .isEqualTo(subscriptionId2);
  }

  @Test
  public void shouldRemoveConnectionWithSingleSubscriptions() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);

    final Long subscriptionId1 = subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().containsKey(CONNECTION_ID))
        .isTrue();
    assertThat(subscriptionManager.getConnectionSubscriptionsMap().get(CONNECTION_ID).size())
        .isEqualTo(1);

    final UnsubscribeRequest unsubscribeRequest =
        new UnsubscribeRequest(subscriptionId1, CONNECTION_ID);
    subscriptionManager.unsubscribe(unsubscribeRequest);

    assertThat(subscriptionManager.getConnectionSubscriptionsMap().isEmpty()).isTrue();
  }

  @Test
  public void getSubscriptionsOfCorrectTypeReturnExpectedSubscriptions() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    subscriptionManager.subscribe(subscribeRequest);

    final List<NewBlockHeadersSubscription> subscriptions =
        subscriptionManager.subscriptionsOfType(
            SubscriptionType.NEW_BLOCK_HEADERS, NewBlockHeadersSubscription.class);

    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0)).isInstanceOf(NewBlockHeadersSubscription.class);
  }

  @Test
  public void getSubscriptionsOfWrongTypeReturnEmptyList() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    subscriptionManager.subscribe(subscribeRequest);

    final List<NewBlockHeadersSubscription> subscriptions =
        subscriptionManager.subscriptionsOfType(
            SubscriptionType.SYNCING, NewBlockHeadersSubscription.class);

    assertThat(subscriptions).hasSize(0);
  }

  @Test
  public void unsubscribeWithUnknownConnectionId() {
    final SubscribeRequest subscribeRequestOne =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);
    final long subscriptionId = subscriptionManager.subscribe(subscribeRequestOne);

    final boolean success =
        subscriptionManager.unsubscribe(
            new UnsubscribeRequest(subscriptionId, "unknown-connection-id"));

    assertThat(success).isTrue();
  }

  // TODO vertx event bus testing for response
}
