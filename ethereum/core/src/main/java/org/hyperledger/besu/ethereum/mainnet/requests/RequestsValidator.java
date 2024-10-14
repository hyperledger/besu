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
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates requests within a block against a set of predefined validators. This class delegates
 * the validation of requests of specific types to corresponding validators. It ensures that
 * requests are properly ordered, have a valid root, and meet the criteria defined by their
 * validators.
 */
public class RequestsValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RequestsValidator.class);

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

    if (!isRequestsHashValid(block, maybeRequests)) {
      return false;
    }

    if (!isRequestOrderValid(maybeRequests.get())) {
      final Hash blockHash = block.getHash();
      LOG.warn("Block {} the ordering across requests must be ascending by type", blockHash);
      return false;
    }
    return true;
  }

  private boolean isRequestsHashValid(final Block block, final Optional<List<Request>> requests) {
    final Hash blockHash = block.getHash();
    final Optional<Hash> maybeRequestsHash = block.getHeader().getRequestsHash();

    if (maybeRequestsHash.isEmpty()) {
      LOG.warn("Block {} must contain requests root", blockHash);
      return false;
    }

    if (requests.isEmpty()) {
      LOG.warn("Block {} must contain requests (even if empty list)", blockHash);
      return false;
    }

    final Hash expectedRequestsHash = BodyValidation.requestsHash(requests.get());
    if (!expectedRequestsHash.equals(maybeRequestsHash.get())) {
      LOG.warn(
          "Block {} requests root does not match expected hash root for requests in block",
          blockHash);
      return false;
    }
    return true;
  }

  private static boolean isRequestOrderValid(final List<Request> requests) {
    return IntStream.range(0, requests.size() - 1)
        .allMatch(i -> requests.get(i).getType().compareTo(requests.get(i + 1).getType()) <= 0);
  }
}
