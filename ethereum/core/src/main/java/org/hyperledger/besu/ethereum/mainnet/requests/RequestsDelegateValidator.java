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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestsDelegateValidator implements RequestValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RequestValidator.class);
  private final ImmutableMap<RequestType, RequestValidator> validators;

  public RequestsDelegateValidator(final ImmutableMap<RequestType, RequestValidator> validators) {
    this.validators = validators;
  }

  @Override
  public boolean validate(final Block block, final List<Request> requests) {
    if (!validateRequestSorting(requests)) {
      final Hash blockHash = block.getHash();
      LOG.warn("Block {} the ordering across requests must be ascending by type", blockHash);
    }
    if (!validateRequestRoot(block, requests)) {
      return false;
    }
    for (final RequestType type : requestTypes(requests)) {
      if (!validateRequest(type, block, requests)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> request) {
    return request.isPresent();
  }

  private boolean validateRequestRoot(final Block block, final List<Request> requests) {
    final Hash blockHash = block.getHash();
    if (block.getHeader().getRequestsRoot().isEmpty()) {
      LOG.warn("Block {} must contain requests root", blockHash);
      return false;
    }

    if (block.getBody().getRequests().isEmpty()) {
      LOG.warn("Block {} must contain requests (even if empty list)", blockHash);
      return false;
    }

    // Validate requests root
    final Hash expectedRequestRoot = BodyValidation.requestsRoot(requests);
    if (!expectedRequestRoot.equals(block.getHeader().getRequestsRoot().get())) {
      LOG.warn(
          "Block {} requests root does not match expected hash root for requests in block",
          blockHash);
      return false;
    }
    return true;
  }

  /**
   * Validates requests of a specific type within a block.
   *
   * @param type The type of requests to validate.
   * @param block The block containing the requests.
   * @param requests The list of requests to validate.
   * @return true if the requests are valid, false otherwise.
   */
  private boolean validateRequest(
      final RequestType type, final Block block, final List<Request> requests) {
    return getRequestValidator(type)
        .map(validator -> validator.validate(block, requests))
        .orElseGet(
            () -> {
              LOG.warn("Block {} contains prohibited requests of type: {}", block.getHash(), type);
              return false;
            });
  }

  /**
   * Retrieves a validator for the specified request type, if available.
   *
   * @param requestType The type of request for which a validator is sought.
   * @return An Optional containing the validator, if found.
   */
  private Optional<RequestValidator> getRequestValidator(final RequestType requestType) {
    return Optional.ofNullable(validators.get(requestType));
  }

  public static Set<RequestType> requestTypes(final List<Request> requests) {
    return requests.stream().map(Request::getType).collect(Collectors.toSet());
  }

  private static boolean validateRequestSorting(final List<Request> requests) {
    return IntStream.range(0, requests.size() - 1)
        .allMatch(i -> requests.get(i).getType().compareTo(requests.get(i + 1).getType()) <= 0);
  }
}
