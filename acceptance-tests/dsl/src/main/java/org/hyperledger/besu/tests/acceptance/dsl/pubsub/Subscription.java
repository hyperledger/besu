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
package org.hyperledger.besu.tests.acceptance.dsl.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;

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
    WaitUtils.waitFor(
        () -> {
          assertThat(connection.getSubscriptionEvents()).isNotEmpty();
          final List<SubscriptionEvent> events = connection.getSubscriptionEvents();

          int occurrences = 0;
          for (final SubscriptionEvent event : events) {
            if (matches(expectedTransaction, event)) {
              occurrences++;
            }
          }

          assertThat(occurrences)
              .as("Expecting: %s occurrences, but found: %s", expectedOccurrences, occurrences)
              .isEqualTo(expectedOccurrences);
        });
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
