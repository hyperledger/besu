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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.DepositDecoder;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DepositsValidator {

  boolean validateDepositParameter(Optional<List<Deposit>> deposits);

  boolean validateDeposits(Block block, List<TransactionReceipt> receipts);

  boolean validateDepositsRoot(Block block);

  class ProhibitedDeposits implements DepositsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ProhibitedDeposits.class);

    @Override
    public boolean validateDepositParameter(final Optional<List<Deposit>> deposits) {
      return deposits.isEmpty();
    }

    @Override
    public boolean validateDeposits(final Block block, final List<TransactionReceipt> receipts) {
      Optional<List<Deposit>> deposits = block.getBody().getDeposits();
      final boolean isValid = deposits.isEmpty();
      if (!isValid) {
        LOG.warn("Deposits must be empty when Deposits are prohibited but were: {}", deposits);
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
    private final Address depositContractAddress;

    public AllowedDeposits(final Address depositContractAddress) {
      this.depositContractAddress = depositContractAddress;
    }

    @Override
    public boolean validateDepositParameter(final Optional<List<Deposit>> deposits) {
      return deposits.isPresent();
    }

    @Override
    public boolean validateDeposits(final Block block, final List<TransactionReceipt> receipts) {
      if (block.getBody().getDeposits().isEmpty()) {
        LOG.warn("Deposits must not be empty when Deposits are activated");
        return false;
      }

      List<Deposit> actualDeposits = new ArrayList<>(block.getBody().getDeposits().get());
      List<Deposit> expectedDeposits = new ArrayList<>();

      for (TransactionReceipt receipt : receipts) {
        for (Log log : receipt.getLogsList()) {
          if (depositContractAddress.equals(log.getLogger())) {
            Deposit deposit = DepositDecoder.decodeFromLog(log);
            expectedDeposits.add(deposit);
          }
        }
      }

      boolean isValid = actualDeposits.equals(expectedDeposits);

      if (!isValid) {
        LOG.warn(
            "Deposits validation failed. Deposits from block body do not match deposits from logs. Block hash: {}",
            block.getHash());
        LOG.debug(
            "Deposits from logs: {}, deposits from block body: {}",
            expectedDeposits,
            actualDeposits);
      }

      return isValid;
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
            "Invalid block: depositsRoot mismatch (expected={}, actual={})",
            expectedDepositsRoot,
            depositsRoot.get());
        return false;
      }

      return true;
    }
  }
}
