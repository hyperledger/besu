/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Engine payload status result. */
@JsonPropertyOrder({"status", "latestValidHash", "validationError"})
public class EnginePayloadStatusResult {
  /** The Status. */
  EngineStatus status;

  /** The Latest valid hash. */
  Optional<Hash> latestValidHash;

  /** The Validation error. */
  Optional<String> validationError;

  /**
   * Instantiates a new Engine payload status result.
   *
   * @param status the status
   * @param latestValidHash the latest valid hash
   * @param validationError the validation error
   */
  public EnginePayloadStatusResult(
      final EngineStatus status,
      final Hash latestValidHash,
      final Optional<String> validationError) {
    this.status = status;
    this.latestValidHash = Optional.ofNullable(latestValidHash);
    this.validationError = validationError;
  }

  /**
   * Gets status as string.
   *
   * @return the status as string
   */
  @JsonGetter(value = "status")
  public String getStatusAsString() {
    return status.name();
  }

  /**
   * Gets status.
   *
   * @return the status
   */
  public EngineStatus getStatus() {
    return status;
  }

  /**
   * Gets latest valid hash as string.
   *
   * @return the latest valid hash as string
   */
  @JsonGetter(value = "latestValidHash")
  public String getLatestValidHashAsString() {
    return latestValidHash.map(Hash::toHexString).orElse(null);
  }

  /**
   * Gets latest valid hash.
   *
   * @return the latest valid hash
   */
  public Optional<Hash> getLatestValidHash() {
    return latestValidHash;
  }

  /**
   * Gets error.
   *
   * @return the error
   */
  @JsonGetter(value = "validationError")
  public String getError() {
    return validationError.orElse(null);
  }
}
