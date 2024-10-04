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

import static org.hyperledger.besu.ethereum.mainnet.requests.RequestUtil.getConsolidationRequests;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsolidationRequestValidator implements RequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(ConsolidationRequestValidator.class);

  public static final int MAX_CONSOLIDATION_REQUESTS_PER_BLOCK = 1;

  private boolean validateConsolidationRequestParameter(
      final Optional<List<ConsolidationRequest>> consolidationRequests) {
    return consolidationRequests.isPresent();
  }

  private boolean validateConsolidationRequestsInBlock(
      final Block block, final List<ConsolidationRequest> consolidationRequests) {
    final Hash blockHash = block.getHash();

    final List<ConsolidationRequest> consolidationRequestsInBlock =
        block
            .getBody()
            .getRequests()
            .flatMap(requests -> getConsolidationRequests(Optional.of(requests)))
            .orElse(Collections.emptyList());

    if (consolidationRequestsInBlock.size() > MAX_CONSOLIDATION_REQUESTS_PER_BLOCK) {
      LOG.warn(
          "Block {} has more than the allowed maximum number of consolidation requests", blockHash);
      return false;
    }

    // Validate ConsolidationRequests
    final boolean expectedConsolidationRequestMatch =
        consolidationRequests.equals(consolidationRequestsInBlock);
    if (!expectedConsolidationRequestMatch) {
      LOG.warn(
          "Block {} has a mismatch between block consolidations and RPC consolidation requests (in_block = {}, "
              + "expected = {})",
          blockHash,
          consolidationRequestsInBlock,
          consolidationRequests);
      return false;
    }
    return true;
  }

  @Override
  public boolean validate(
      final Block block, final List<Request> requests, final List<TransactionReceipt> receipts) {
    var consolidationRequests =
        getConsolidationRequests(Optional.of(requests)).orElse(Collections.emptyList());
    return validateConsolidationRequestsInBlock(block, consolidationRequests);
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> request) {
    if (request.isEmpty()) {
      return true;
    }
    var consolidationRequests =
        RequestUtil.filterRequestsOfType(request.get(), ConsolidationRequest.class);
    return validateConsolidationRequestParameter(Optional.of(consolidationRequests));
  }
}
