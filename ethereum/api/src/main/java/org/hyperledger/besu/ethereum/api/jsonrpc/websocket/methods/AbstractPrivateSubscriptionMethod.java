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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

/** The type Abstract private subscription method. */
abstract class AbstractPrivateSubscriptionMethod extends AbstractSubscriptionMethod {

  /** The Privacy controller. */
  final PrivacyController privacyController;

  /** The Privacy id provider. */
  protected final PrivacyIdProvider privacyIdProvider;

  /**
   * Instantiates a new Abstract private subscription method.
   *
   * @param subscriptionManager the subscription manager
   * @param mapper the mapper
   * @param privacyController the privacy controller
   * @param privacyIdProvider the privacy id provider
   */
  AbstractPrivateSubscriptionMethod(
      final SubscriptionManager subscriptionManager,
      final SubscriptionRequestMapper mapper,
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider) {
    super(subscriptionManager, mapper);
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  /**
   * Check if privacy group matches authenticated privacy user id.
   *
   * @param request the request
   * @param privacyGroupId the privacy group id
   */
  void checkIfPrivacyGroupMatchesAuthenticatedPrivacyUserId(
      final JsonRpcRequestContext request, final String privacyGroupId) {
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
  }
}
