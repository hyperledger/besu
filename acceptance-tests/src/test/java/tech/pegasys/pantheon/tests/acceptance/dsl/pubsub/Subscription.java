package tech.pegasys.pantheon.tests.acceptance.dsl.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.List;
import java.util.Map;

public class Subscription {

  private static final String SIXTY_FOUR_HEX_PATTERN = "0x[0-9a-f]{64}";
  private static final String HEX_PATTERN = "0x[0-9a-f]+";

  private final WebSocketConnection connection;
  private final String value;

  public Subscription(final WebSocketConnection connection, final String value) {
    assertThat(value).matches(HEX_PATTERN);
    assertThat(connection).isNotNull();
    this.value = value;
    this.connection = connection;
  }

  @Override
  public String toString() {
    return value;
  }

  public void verifyEventReceived(final Hash expectedTransaction) {
    verifyEventReceived(expectedTransaction, 1);
  }

  public void verifyEventReceived(final Hash expectedTransaction, final int expectedOccurrences) {
    final List<SubscriptionEvent> events = connection.getSubscriptionEvents();
    assertThat(events).isNotNull();
    int occurrences = 0;

    for (final SubscriptionEvent event : events) {
      if (matches(expectedTransaction, event)) {
        occurrences++;
      }
    }

    assertThat(occurrences)
        .as("Expecting: %s occurrences, but found: %s", expectedOccurrences, occurrences)
        .isEqualTo(expectedOccurrences);
  }

  private boolean matches(final Hash expectedTransaction, final SubscriptionEvent event) {
    return isEthSubscription(event)
        && isExpectedSubscription(event)
        && isExpectedTransaction(expectedTransaction, event);
  }

  private boolean isEthSubscription(final SubscriptionEvent event) {
    return "2.0".equals(event.getVersion())
        && "eth_subscription".equals(event.getMethod())
        && event.getParams() != null;
  }

  private boolean isExpectedSubscription(final SubscriptionEvent event) {
    final Map<String, String> params = event.getParams();
    return params.size() == 2
        && params.containsKey("subscription")
        && value.equals(params.get("subscription"));
  }

  private boolean isExpectedTransaction(
      final Hash expectedTransaction, final SubscriptionEvent event) {
    final Map<String, String> params = event.getParams();
    final String result = params.get("result");
    return params.containsKey("result")
        && expectedTransaction.toString().equals(result)
        && result.matches(SIXTY_FOUR_HEX_PATTERN);
  }
}
