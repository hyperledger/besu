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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.PermissionTransactionFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

/**
 * Validates a transaction based on Frontier protocol runtime requirements.
 *
 * <p>The {@link PermissionTransactionValidator} performs the intrinsic gas cost check on the given
 * {@link Transaction}.
 */
public class PermissionTransactionValidator implements TransactionValidator {
  private final TransactionValidator delegate;
  private final PermissionTransactionFilter permissionTransactionFilter;

  public PermissionTransactionValidator(
      final TransactionValidator delegate,
      final PermissionTransactionFilter permissionTransactionFilter) {
    this.delegate = delegate;
    this.permissionTransactionFilter = permissionTransactionFilter;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final Optional<Wei> blobBaseFee,
      final TransactionValidationParams transactionValidationParams) {
    return delegate.validate(transaction, baseFee, blobBaseFee, transactionValidationParams);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {

    if (!isSenderAllowed(transaction, validationParams)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format("Sender %s is not on the Account Allowlist", transaction.getSender()));
    }

    return delegate.validateForSender(transaction, sender, validationParams);
  }

  private boolean isSenderAllowed(
      final Transaction transaction, final TransactionValidationParams validationParams) {
    if (validationParams.checkLocalPermissions() || validationParams.checkOnchainPermissions()) {
      return permissionTransactionFilter.permitted(
          transaction,
          validationParams.checkLocalPermissions(),
          validationParams.checkOnchainPermissions());
    }
    return true;
  }
}
