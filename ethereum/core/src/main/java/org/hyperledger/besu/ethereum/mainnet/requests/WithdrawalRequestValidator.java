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

import static org.hyperledger.besu.ethereum.mainnet.requests.RequestUtil.getWithdrawalRequests;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithdrawalRequestValidator implements RequestValidator {

  public static final int MAX_WITHDRAWAL_REQUESTS_PER_BLOCK = 16;

  private static final Logger LOG = LoggerFactory.getLogger(WithdrawalRequestValidator.class);

  private boolean validateWithdrawalRequestParameter(
      final Optional<List<WithdrawalRequest>> withdrawalRequests) {
    return withdrawalRequests.isPresent();
  }

  private boolean validateWithdrawalRequestsInBlock(
      final Block block, final List<WithdrawalRequest> withdrawalRequests) {
    final Hash blockHash = block.getHash();

    final List<WithdrawalRequest> withdrawalRequestsInBlock =
        block
            .getBody()
            .getRequests()
            .flatMap(requests -> getWithdrawalRequests(Optional.of(requests)))
            .orElse(Collections.emptyList());

    // TODO Do we need to allow for customization? (e.g. if the value changes in the next fork)
    if (withdrawalRequestsInBlock.size() > MAX_WITHDRAWAL_REQUESTS_PER_BLOCK) {
      LOG.warn(
          "Block {} has more than the allowed maximum number of withdrawal requests", blockHash);
      return false;
    }

    // Validate WithdrawalRequests
    final boolean expectedWithdrawalRequestMatch =
        withdrawalRequests.equals(withdrawalRequestsInBlock);
    if (!expectedWithdrawalRequestMatch) {
      LOG.warn(
          "Block {} has a mismatch between its withdrawal requests and expected withdrawal requests (in_block = {}, "
              + "expected = {})",
          blockHash,
          withdrawalRequestsInBlock,
          withdrawalRequests);
      return false;
    }
    return true;
  }

  @Override
  public boolean validate(
      final Block block, final List<Request> requests, final List<TransactionReceipt> receipts) {
    var withdrawalRequests =
        getWithdrawalRequests(Optional.of(requests)).orElse(Collections.emptyList());
    return validateWithdrawalRequestsInBlock(block, withdrawalRequests);
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> request) {
    if (request.isEmpty()) {
      return false;
    }
    var withdrawalRequests =
        RequestUtil.filterRequestsOfType(request.get(), WithdrawalRequest.class);
    return validateWithdrawalRequestParameter(Optional.of(withdrawalRequests));
  }
}
