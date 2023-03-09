/*
 * Copyright contributors to Hyperledger Besu
 *
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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Deposit;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DepositsValidator {

  boolean validateDeposits(Optional<List<Deposit>> deposits);

  boolean validateDepositsRoot(Block block);

  class ProhibitedDeposits implements DepositsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ProhibitedDeposits.class);

    @Override
    public boolean validateDeposits(final Optional<List<Deposit>> deposits) {
      final boolean isValid = deposits.isEmpty();
      if (!isValid) {
        LOG.warn("Deposits must be null when Deposits are prohibited but were: {}", deposits);
      }
      return isValid;
    }

    @Override
    public boolean validateDepositsRoot(final Block block) {
      final Optional<Hash> depositsRoot = block.getHeader().getDepositsRoot();
      if (depositsRoot.isPresent()) {
        LOG.warn(
            "DepositsRoot must be null when Deposits are prohibited but was: {}",
            depositsRoot.get());
        return false;
      }

      return true;
    }
  }

  class AllowedDeposits implements DepositsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AllowedDeposits.class);

    @Override
    public boolean validateDeposits(final Optional<List<Deposit>> deposits) {
      return true; // TODO 6110: Validate deposits in the next PR
    }

    @Override
    public boolean validateDepositsRoot(final Block block) {
      checkArgument(block.getBody().getDeposits().isPresent(), "Block body must contain deposits");
      final Optional<Hash> depositsRoot = block.getHeader().getDepositsRoot();
      if (depositsRoot.isEmpty()) {
        LOG.warn("depositsRoot must not be null when Deposits are activated");
        return false;
      }

      final List<Deposit> deposits = block.getBody().getDeposits().get();
      final Hash expectedDepositsRoot = BodyValidation.depositsRoot(deposits);
      if (!expectedDepositsRoot.equals(depositsRoot.get())) {
        LOG.info(
            "Invalid block: transaction root mismatch (expected={}, actual={})",
            expectedDepositsRoot,
            depositsRoot.get());
        return false;
      }

      return true;
    }
  }
}
