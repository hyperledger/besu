/*
 * Copyright contributors to Besu.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProhibitedRequestValidator implements RequestsValidator {
  private static final Logger LOG = LoggerFactory.getLogger(MainnetRequestsValidator.class);

  @Override
  public boolean validate(final Optional<List<Request>> maybeRequests) {
    boolean hasRequests = maybeRequests.isPresent();

    if (hasRequests) {
      LOG.warn("There are requests but requests are prohibited");
    }

    return !hasRequests;
  }
}
