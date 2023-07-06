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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription;

import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscribeRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.response.SubscriptionResponse;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.UUID;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class SubscriptionManagerSendMessageTest {

  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;

  private Vertx vertx;
  private SubscriptionManager subscriptionManager;

  @Before
  public void before(final TestContext context) {
    vertx = Vertx.vertx();
    subscriptionManager = new SubscriptionManager(new NoOpMetricsSystem());
    vertx.deployVerticle(subscriptionManager, context.asyncAssertSuccess());
  }

  @Test
  @Ignore
  public void shouldSendMessageOnTheConnectionIdEventBusAddressForExistingSubscription(
      final TestContext context) {
    final String connectionId = UUID.randomUUID().toString();
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, connectionId);

    final JsonRpcResult expectedResult = mock(JsonRpcResult.class);
    final Subscription subscription =
        new Subscription(1L, connectionId, SubscriptionType.SYNCING, false);
    final SubscriptionResponse expectedResponse =
        new SubscriptionResponse(subscription, expectedResult);

    final Long subscriptionId = subscriptionManager.subscribe(subscribeRequest);

    final Async async = context.async();

    vertx
        .eventBus()
        .consumer(connectionId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              async.complete();
            })
        .completionHandler(v -> subscriptionManager.sendMessage(subscriptionId, expectedResult));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void shouldNotSendMessageOnTheConnectionIdEventBusAddressForAbsentSubscription(
      final TestContext context) {
    final String connectionId = UUID.randomUUID().toString();

    final Async async = context.async();

    vertx
        .eventBus()
        .consumer(connectionId)
        .handler(
            msg -> {
              fail("Shouldn't receive message");
              async.complete();
            })
        .completionHandler(v -> subscriptionManager.sendMessage(1L, mock(JsonRpcResult.class)));

    // if it doesn't receive the message in 5 seconds we assume it won't receive anymore
    vertx.setPeriodic(5000, v -> async.complete());

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }
}
