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

import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Ordering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates requests within a block against a set of predefined validators. This class delegates
 * the validation of requests of specific types to corresponding validators. It ensures that
 * requests are properly ordered, have a valid hash, and meet the criteria defined by their
 * validators.
 */
public class MainnetRequestsValidator implements RequestsValidator {
  private static final Logger LOG = LoggerFactory.getLogger(MainnetRequestsValidator.class);

  /**
   * Validates a block's requests by ensuring they are correctly ordered, have a valid hash, and
   * pass their respective type-specific validations.
   *
   * @param maybeRequests The list of requests to be validated.
   * @return true if all validations pass; false otherwise.
   */
  @Override
  public boolean validate(final Optional<List<Request>> maybeRequests) {
    if (maybeRequests.isEmpty()) {
      LOG.warn("Must contain requests (even if empty list)");
      return false;
    }

    if (!isRequestOrderValid(maybeRequests.get())) {
      LOG.warn("Ordering across requests must be ascending by type");
      return false;
    }

    return true;
  }

  private static boolean isRequestOrderValid(final List<Request> requests) {
    return Ordering.natural().onResultOf(Request::getType).isOrdered(requests);
  }
}
