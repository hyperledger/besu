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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EngineForkchoiceUpdatedParameter {
  private final Hash headBlockHash;

  private final Hash safeBlockHash;

  private final Hash finalizedBlockHash;

  public Hash getHeadBlockHash() {
    return headBlockHash;
  }

  public Hash getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  public Hash getSafeBlockHash() {
    return safeBlockHash;
  }

  @JsonCreator
  public EngineForkchoiceUpdatedParameter(
      @JsonProperty("headBlockHash") final Hash headBlockHash,
      @JsonProperty("finalizedBlockHash") final Hash finalizedBlockHash,
      @JsonProperty("safeBlockHash") final Hash safeBlockHash) {
    this.finalizedBlockHash = finalizedBlockHash;
    this.headBlockHash = headBlockHash;
    this.safeBlockHash = safeBlockHash;
  }
}
