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

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfiguration;

import io.vertx.core.Vertx;

public class WebSocket {

  private static final String HEX_PATTERN = "0x[0-9a-f]+";

  private final WebSocketConnection connection;

  public WebSocket(final Vertx vertx, final NodeConfiguration node) {
    this.connection = new WebSocketConnection(vertx, node);
  }

  public Subscription subscribe() {
    final JsonRpcSuccessEvent subscribe = connection.subscribe("newPendingTransactions");

    assertThat(subscribe).isNotNull();
    assertThat(subscribe.getVersion()).isEqualTo("2.0");
    assertThat(subscribe.getId()).isGreaterThan(0);
    assertThat(subscribe.getResult()).matches(HEX_PATTERN);

    return new Subscription(connection, subscribe.getResult());
  }

  public void unsubscribe(final Subscription subscription) {
    final JsonRpcSuccessEvent unsubscribe = connection.unsubscribe(subscription);

    assertThat(unsubscribe).isNotNull();
    assertThat(unsubscribe.getVersion()).isEqualTo("2.0");
    assertThat(unsubscribe.getId()).isGreaterThan(0);
    assertThat(unsubscribe.getResult()).isEqualTo("true");
  }

  public void verifyTotalEventsReceived(final int expectedTotalEventCount) {
    WaitUtils.waitFor(
        () -> assertThat(connection.getSubscriptionEvents()).hasSize(expectedTotalEventCount));
  }
}
