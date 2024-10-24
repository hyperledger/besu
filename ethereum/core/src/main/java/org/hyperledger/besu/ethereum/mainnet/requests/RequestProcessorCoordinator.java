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
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;

import com.google.common.collect.ImmutableSortedMap;

/** Processes various types of requests based on their RequestType. */
public class RequestProcessorCoordinator {
  private final ImmutableSortedMap<RequestType, RequestProcessor> processors;

  /**
   * Constructs a RequestsProcessor with a given map of processors.
   *
   * @param processors A map associating RequestType with their corresponding RequestProcessor.
   */
  private RequestProcessorCoordinator(
      final ImmutableSortedMap<RequestType, RequestProcessor> processors) {
    this.processors = processors;
  }

  public List<Request> process(final ProcessRequestContext context) {
    return processors.values().stream()
        .map(requestProcessor -> requestProcessor.process(context))
        .toList();
  }

  public static class Builder {
    private final ImmutableSortedMap.Builder<RequestType, RequestProcessor>
        requestProcessorBuilder = ImmutableSortedMap.naturalOrder();

    public RequestProcessorCoordinator.Builder addProcessor(
        final RequestType type, final RequestProcessor processor) {
      this.requestProcessorBuilder.put(type, processor);
      return this;
    }

    public RequestProcessorCoordinator build() {
      final ImmutableSortedMap<RequestType, RequestProcessor> processors =
          requestProcessorBuilder.build();
      if (processors.isEmpty()) {
        throw new IllegalStateException("No processors added to RequestProcessorCoordinator");
      }
      return new RequestProcessorCoordinator(processors);
    }
  }
}
