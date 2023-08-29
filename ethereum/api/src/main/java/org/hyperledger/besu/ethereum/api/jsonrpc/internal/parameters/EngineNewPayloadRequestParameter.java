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

import java.util.List;
import java.util.Optional;

/** Represents the parameters for a new payload request in the Ethereum engine. */
public class EngineNewPayloadRequestParameter {

  private final EngineExecutionPayloadParameter executionPayload;
  private final Optional<List<String>> expectedBlobVersionedHashes;
  private final Optional<String> parentBeaconBlockRoot;

  /**
   * Constructs a new payload request parameter with only an execution payload.
   *
   * @param payload the execution payload
   */
  public EngineNewPayloadRequestParameter(final EngineExecutionPayloadParameter payload) {
    this(payload, Optional.empty(), Optional.empty());
  }

  /**
   * Constructs a new payload request parameter with an execution payload, expected blob versioned
   * hashes, and a parent beacon block root.
   *
   * @param executionPayload the execution payload
   * @param expectedBlobVersionedHashes the expected blob versioned hashes
   * @param parentBeaconBlockRoot the parent beacon block root
   */
  public EngineNewPayloadRequestParameter(
      final EngineExecutionPayloadParameter executionPayload,
      final Optional<List<String>> expectedBlobVersionedHashes,
      final Optional<String> parentBeaconBlockRoot) {
    this.executionPayload = executionPayload;
    this.expectedBlobVersionedHashes = expectedBlobVersionedHashes;
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
  }

  /**
   * Returns the execution payload.
   *
   * @return the execution payload
   */
  public EngineExecutionPayloadParameter getExecutionPayload() {
    return executionPayload;
  }

  /**
   * Returns the expected blob versioned hashes.
   *
   * @return the expected blob versioned hashes
   */
  public Optional<List<String>> getExpectedBlobVersionedHashes() {
    return expectedBlobVersionedHashes;
  }

  /**
   * Returns the parent beacon block root.
   *
   * @return the parent beacon block root
   */
  public Optional<String> getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }
}
