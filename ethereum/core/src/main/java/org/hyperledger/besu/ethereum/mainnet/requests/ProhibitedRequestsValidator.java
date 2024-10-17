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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates that a block does not contain any prohibited requests. */
public class ProhibitedRequestsValidator implements RequestValidator {
  private static final Logger LOG = LoggerFactory.getLogger(ProhibitedRequestsValidator.class);

  @Override
  public boolean validate(
      final Block block, final List<Request> request, final List<TransactionReceipt> receipts) {
    boolean hasRequestsHash = block.getHeader().getRequestsHash().isPresent();

    if (hasRequestsHash) {
      LOG.warn(
          "Block {} header contains requests_hash but requests are prohibited", block.getHash());
    }

    return !hasRequestsHash;
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> request) {
    return request.isEmpty();
  }
}
