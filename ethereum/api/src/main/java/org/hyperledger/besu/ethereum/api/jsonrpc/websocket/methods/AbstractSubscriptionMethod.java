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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

/** The type Abstract subscription method. */
abstract class AbstractSubscriptionMethod implements JsonRpcMethod {

  private final SubscriptionManager subscriptionManager;
  private final SubscriptionRequestMapper mapper;

  /**
   * Instantiates a new Abstract subscription method.
   *
   * @param subscriptionManager the subscription manager
   * @param mapper the mapper
   */
  AbstractSubscriptionMethod(
      final SubscriptionManager subscriptionManager, final SubscriptionRequestMapper mapper) {
    this.subscriptionManager = subscriptionManager;
    this.mapper = mapper;
  }

  /**
   * Subscription manager subscription manager.
   *
   * @return the subscription manager
   */
  SubscriptionManager subscriptionManager() {
    return subscriptionManager;
  }

  /**
   * Gets mapper.
   *
   * @return the mapper
   */
  public SubscriptionRequestMapper getMapper() {
    return mapper;
  }
}
