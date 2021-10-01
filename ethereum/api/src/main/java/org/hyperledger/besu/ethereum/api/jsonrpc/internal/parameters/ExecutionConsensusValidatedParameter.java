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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ConsensusStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionConsensusValidatedParameter {
  private final Hash blockHash;
  private final ConsensusStatus status;

  @JsonCreator
  public ExecutionConsensusValidatedParameter(
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("status") final ConsensusStatus status) {
    this.blockHash = blockHash;
    this.status = status;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public ConsensusStatus getStatus() {
    return status;
  }
}
