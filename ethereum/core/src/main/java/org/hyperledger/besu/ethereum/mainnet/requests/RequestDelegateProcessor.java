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
package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

/** Processes various types of requests based on their RequestType. */
public class RequestDelegateProcessor implements RequestProcessor {
  private final ImmutableMap<RequestType, RequestProcessor> processors;

  /**
   * Constructs a RequestsProcessor with a given map of processors.
   *
   * @param processors A map associating RequestType with their corresponding RequestProcessor.
   */
  public RequestDelegateProcessor(final ImmutableMap<RequestType, RequestProcessor> processors) {
    this.processors = processors;
  }

  /**
   * Processes all requests for the available request types in the processors map.
   *
   * @param mutableWorldState The mutable world state to be used by the processors.
   * @return A list of requests.
   */
  @Override
  public Optional<List<Request>> process(final MutableWorldState mutableWorldState) {
    List<Request> requests = new ArrayList<>();
    processors.values().stream()
        .map(processor -> processor.process(mutableWorldState))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(requests::addAll);
    return Optional.of(requests);
  }
}
