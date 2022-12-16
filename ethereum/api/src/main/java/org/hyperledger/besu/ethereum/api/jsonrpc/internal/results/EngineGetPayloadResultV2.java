/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "executionPayload",
  "blockValue",
})
public class EngineGetPayloadResultV2 {
  protected final EngineGetPayloadResult executionPayload;
  private final String blockValue;

  public EngineGetPayloadResultV2(
      final EngineGetPayloadResult executionPayload, final String blockValue) {
    this.executionPayload = executionPayload;
    this.blockValue = blockValue;
  }

  @JsonGetter(value = "executionPayload")
  public EngineGetPayloadResult getExecutionPayload() {
    return executionPayload;
  }

  @JsonGetter(value = "blockValue")
  public String getBlockValue() {
    return blockValue;
  }
}
