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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PragueWithdrawalRequestValidator implements WithdrawalRequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(PragueWithdrawalRequestValidator.class);

  @Override
  public boolean allowWithdrawalRequests() {
    return true;
  }

  @Override
  public boolean validateWithdrawalRequestParameter(
      final Optional<List<WithdrawalRequest>> withdrawalRequests) {
    return withdrawalRequests.isPresent();
  }

  @Override
  public boolean validateWithdrawalRequestsInBlock(
      final Block block, final List<WithdrawalRequest> withdrawalRequests) {
    final Hash blockHash = block.getHash();

    if (block.getHeader().getWithdrawalRequestsRoot().isEmpty()) {
      LOG.warn("Block {} must contain withdrawal_requests_root", blockHash);
      return false;
    }

    if (block.getBody().getWithdrawalRequests().isEmpty()) {
      LOG.warn("Block {} must contain withdrawal requests (even if empty list)", blockHash);
      return false;
    }

    final List<WithdrawalRequest> withdrawalRequestsInBlock =
        block.getBody().getWithdrawalRequests().get();
    // TODO Do we need to allow for customization? (e.g. if the value changes in the next fork)
    if (withdrawalRequestsInBlock.size()
        > WithdrawalRequestContractHelper.MAX_WITHDRAWAL_REQUESTS_PER_BLOCK) {
      LOG.warn(
          "Block {} has more than the allowed maximum number of withdrawal requests", blockHash);
      return false;
    }

    // Validate exits_root
    final Hash expectedWithdrawalsRequestRoot =
        BodyValidation.withdrawalRequestsRoot(withdrawalRequestsInBlock);
    if (!expectedWithdrawalsRequestRoot.equals(
        block.getHeader().getWithdrawalRequestsRoot().get())) {
      LOG.warn(
          "Block {} withdrawal_requests_root does not match expected hash root for withdrawal requests in block",
          blockHash);
      return false;
    }

    // Validate exits
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
}
