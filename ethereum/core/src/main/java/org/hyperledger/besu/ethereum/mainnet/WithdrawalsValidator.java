/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.core.Withdrawal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WithdrawalsValidator {

  boolean validateWithdrawals(List<Withdrawal> withdrawals);

  class ProhibitedWithdrawals implements WithdrawalsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ProhibitedWithdrawals.class);

    @Override
    public boolean validateWithdrawals(final List<Withdrawal> withdrawals) {
      final boolean isValid = withdrawals == null;
      if (!isValid) {
        LOG.warn(
            "withdrawals must be null when Withdrawals are prohibited but were: {}", withdrawals);
      }
      return isValid;
    }
  }

  class AllowedWithdrawals implements WithdrawalsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AllowedWithdrawals.class);

    @Override
    public boolean validateWithdrawals(final List<Withdrawal> withdrawals) {
      final boolean isValid = withdrawals != null;
      if (!isValid) {
        LOG.warn("withdrawals must not be null when Withdrawals are activated");
      }
      return isValid;
    }
  }
}
