/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.permissioning.account;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountPermissioningController {

  private static final Logger LOG = LoggerFactory.getLogger(AccountPermissioningController.class);

  private final Optional<AccountLocalConfigPermissioningController>
      accountLocalConfigPermissioningController;
  private final Optional<TransactionSmartContractPermissioningController>
      transactionSmartContractPermissioningController;

  public AccountPermissioningController(
      final Optional<AccountLocalConfigPermissioningController>
          accountLocalConfigPermissioningController,
      final Optional<TransactionSmartContractPermissioningController>
          transactionSmartContractPermissioningController) {
    this.accountLocalConfigPermissioningController = accountLocalConfigPermissioningController;
    this.transactionSmartContractPermissioningController =
        transactionSmartContractPermissioningController;
  }

  public boolean isPermitted(
      final Transaction transaction,
      final boolean includeLocalCheck,
      final boolean includeOnchainCheck) {

    final Hash transactionHash = transaction.getHash();
    final Address sender = transaction.getSender();

    LOG.trace("Account permissioning: Checking transaction {}", transactionHash);

    boolean permittedLocal = true;
    boolean permittedOnchain = true;

    if (includeLocalCheck) {
      permittedLocal =
          accountLocalConfigPermissioningController
              .map(c -> c.isPermitted(transaction))
              .orElse(true);
    }

    if (includeOnchainCheck) {
      permittedOnchain =
          transactionSmartContractPermissioningController
              .map(c -> c.isPermitted(transaction))
              .orElse(true);
    }

    final boolean permitted = permittedLocal && permittedOnchain;

    if (permitted) {
      LOG.trace("Account permissioning: Permitted transaction {} from {}", transactionHash, sender);
    } else {
      LOG.trace("Account permissioning: Rejected transaction {} from {}", transactionHash, sender);
    }

    return permitted;
  }

  public Optional<AccountLocalConfigPermissioningController>
      getAccountLocalConfigPermissioningController() {
    return accountLocalConfigPermissioningController;
  }

  @VisibleForTesting
  Optional<TransactionSmartContractPermissioningController>
      getTransactionSmartContractPermissioningController() {
    return transactionSmartContractPermissioningController;
  }
}
