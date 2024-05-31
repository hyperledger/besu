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
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableSortedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates requests within a block against a set of predefined validators. This class delegates
 * the validation of requests of specific types to corresponding validators. It ensures that
 * requests are properly ordered, have a valid root, and meet the criteria defined by their
 * validators.
 */
public class RequestsValidatorCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(RequestsValidatorCoordinator.class);
  private final ImmutableSortedMap<RequestType, RequestValidator> validators;

  public static RequestsValidatorCoordinator empty() {
    return new Builder().build();
  }

  /**
   * Constructs a new RequestsDelegateValidator with a mapping of request types to their respective
   * validators.
   *
   * @param validators An immutable map of request types to their corresponding validators.
   */
  private RequestsValidatorCoordinator(
      final ImmutableSortedMap<RequestType, RequestValidator> validators) {
    this.validators = validators;
  }

  /**
   * Validates a block's requests by ensuring they are correctly ordered, have a valid root, and
   * pass their respective type-specific validations.
   *
   * @param block The block containing the requests to be validated.
   * @param maybeRequests The list of requests contained within the block.
   * @param receipts The list of transaction receipts corresponding to the requests.
   * @return true if all validations pass; false otherwise.
   */
  public boolean validate(
      final Block block,
      final Optional<List<Request>> maybeRequests,
      final List<TransactionReceipt> receipts) {

    if (validators.isEmpty()) {
      return isRequestsEmpty(block, maybeRequests);
    }

    if (!isRequestsRootValid(block, maybeRequests)) {
      return false;
    }

    if (!isRequestOrderValid(maybeRequests.get())) {
      final Hash blockHash = block.getHash();
      LOG.warn("Block {} the ordering across requests must be ascending by type", blockHash);
      return false;
    }
    return validateRequests(block, maybeRequests.get(), receipts);
  }

  /**
   * Validates the requests contained within a block against their respective type-specific
   * validators.
   *
   * @param block The block containing the requests.
   * @param requests The list of requests to be validated.
   * @param receipts The list of transaction receipts corresponding to the requests.
   * @return true if all requests pass their type-specific validations; false otherwise.
   */
  private boolean validateRequests(
      final Block block, final List<Request> requests, final List<TransactionReceipt> receipts) {
    return requestTypes(requests).stream()
        .allMatch(type -> validateRequestOfType(type, block, requests, receipts));
  }

  private boolean isRequestsRootValid(final Block block, final Optional<List<Request>> requests) {
    final Hash blockHash = block.getHash();
    final Optional<Hash> maybeRequestsRoot = block.getHeader().getRequestsRoot();

    if (maybeRequestsRoot.isEmpty()) {
      LOG.warn("Block {} must contain requests root", blockHash);
      return false;
    }

    if (block.getBody().getRequests().isEmpty()) {
      LOG.warn("Block body {} must contain requests (even if empty list)", blockHash);
      return false;
    }

    if (requests.isEmpty()) {
      LOG.warn("Block {} must contain requests (even if empty list)", blockHash);
      return false;
    }

    final Hash expectedRequestsRoot = BodyValidation.requestsRoot(requests.get());
    if (!expectedRequestsRoot.equals(maybeRequestsRoot.get())) {
      LOG.warn(
          "Block {} requests root does not match expected hash root for requests in block",
          blockHash);
      return false;
    }
    return true;
  }

  private boolean isRequestsEmpty(final Block block, final Optional<List<Request>> requests) {
    final Hash blockHash = block.getHash();
    final Optional<Hash> maybeRequestsRoot = block.getHeader().getRequestsRoot();

    if (maybeRequestsRoot.isPresent()) {
      LOG.warn("Block {} must not contain requests root", blockHash);
      return false;
    }

    if (block.getBody().getRequests().isPresent()) {
      LOG.warn("Block body {} must not contain requests", blockHash);
      return false;
    }

    if (requests.isPresent()) {
      LOG.warn("Block {} must not contain requests", blockHash);
      return false;
    }
    return true;
  }

  private boolean validateRequestOfType(
      final RequestType type,
      final Block block,
      final List<Request> requests,
      final List<TransactionReceipt> receipts) {

    Optional<RequestValidator> requestValidator = getRequestValidator(type);
    if (requestValidator.isEmpty()) {
      LOG.warn("Block {} contains prohibited requests of type: {}", block.getHash(), type);
      return false;
    }
    List<Request> typedRequests = filterRequestsOfType(requests, type);
    return requestValidator.get().validate(block, typedRequests, receipts);
  }

  public Optional<RequestValidator> getRequestValidator(final RequestType requestType) {
    return Optional.ofNullable(validators.get(requestType));
  }

  private static Set<RequestType> requestTypes(final List<Request> requests) {
    return requests.stream().map(Request::getType).collect(Collectors.toSet());
  }

  private static boolean isRequestOrderValid(final List<Request> requests) {
    return IntStream.range(0, requests.size() - 1)
        .allMatch(i -> requests.get(i).getType().compareTo(requests.get(i + 1).getType()) <= 0);
  }

  private static List<Request> filterRequestsOfType(
      final List<Request> requests, final RequestType type) {
    return requests.stream()
        .filter(request -> request.getType() == type)
        .collect(Collectors.toList());
  }

  public static class Builder {
    private final ImmutableSortedMap.Builder<RequestType, RequestValidator> validatorsBuilder =
        ImmutableSortedMap.naturalOrder();

    public Builder addValidator(final RequestType type, final RequestValidator validator) {
      this.validatorsBuilder.put(type, validator);
      return this;
    }

    public RequestsValidatorCoordinator build() {
      return new RequestsValidatorCoordinator(validatorsBuilder.build());
    }
  }
}
