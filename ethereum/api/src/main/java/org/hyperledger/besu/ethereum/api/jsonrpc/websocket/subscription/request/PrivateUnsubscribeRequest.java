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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

import java.util.Objects;

public class PrivateUnsubscribeRequest extends UnsubscribeRequest {

  private final String privacyGroupId;

  public PrivateUnsubscribeRequest(
      final Long subscriptionId, final String connectionId, final String privacyGroupId) {
    super(subscriptionId, connectionId);
    this.privacyGroupId = privacyGroupId;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    PrivateUnsubscribeRequest that = (PrivateUnsubscribeRequest) o;
    return privacyGroupId.equals(that.privacyGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), privacyGroupId);
  }
}
