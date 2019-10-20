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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.syncing;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;

import com.fasterxml.jackson.annotation.JsonValue;

public class NotSynchronisingResult implements JsonRpcResult {

  @JsonValue
  public boolean getResult() {
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    return (this == o) || (o != null && getClass() == o.getClass());
  }

  @Override
  public int hashCode() {
    return "NotSyncingResult".hashCode();
  }
}
