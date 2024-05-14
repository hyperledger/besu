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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WithdrawalRequestValidator {

  boolean allowWithdrawalRequests();

  boolean validateWithdrawalRequestParameter(Optional<List<WithdrawalRequest>> withdrawalRequests);

  boolean validateWithdrawalRequestsInBlock(
      Block block, List<WithdrawalRequest> withdrawalRequests);

  /** Used before Prague */
  class ProhibitedWithdrawalRequests implements WithdrawalRequestValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ProhibitedWithdrawalRequests.class);

    @Override
    public boolean allowWithdrawalRequests() {
      return false;
    }

    /**
     * Before Prague we do not expect to have execution layer withdrawal requests, so it is expected
     * the optional parameter will be empty
     *
     * @param withdrawalRequests Optional list of withdrawal requests
     * @return true, if valid, false otherwise
     */
    @Override
    public boolean validateWithdrawalRequestParameter(
        final Optional<List<WithdrawalRequest>> withdrawalRequests) {
      return withdrawalRequests.isEmpty();
    }

    @Override
    public boolean validateWithdrawalRequestsInBlock(
        final Block block, final List<WithdrawalRequest> withdrawalRequests) {
      final Optional<List<WithdrawalRequest>> maybeWithdrawalRequests =
          block.getBody().getWithdrawalRequests();
      if (maybeWithdrawalRequests.isPresent()) {
        LOG.warn(
            "Block {} contains withdrawal requests but withdrawal requests are prohibited",
            block.getHash());
        return false;
      }

      if (block.getHeader().getWithdrawalRequestsRoot().isPresent()) {
        LOG.warn(
            "Block {} header contains withdrawal_requests_root but withdrawal requests are prohibited",
            block.getHash());
        return false;
      }

      return true;
    }
  }
}
