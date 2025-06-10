/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.plugin.services.txvalidator.TransactionValidationRule;

import java.util.List;
import java.util.Optional;

/**
 * Validates a transaction based on mainnet rule and other custom rules that can added by plugins.
 * Note that custom validation rules can only be more restrictive and cannot override standard
 * mainnet rules.
 */
public class ExtendableTransactionValidator implements TransactionValidator {
  private final TransactionValidator delegate;
  private final List<TransactionValidationRule> additionalValidationRules;

  public ExtendableTransactionValidator(
      final TransactionValidator delegate,
      final List<TransactionValidationRule> additionalValidationRules) {
    this.delegate = delegate;
    this.additionalValidationRules = additionalValidationRules;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final Optional<Wei> blobBaseFee,
      final TransactionValidationParams transactionValidationParams) {

    final var maybeInvalidReason = additionalValidation(transaction);
    if (maybeInvalidReason.isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.PLUGIN_TX_VALIDATOR, maybeInvalidReason.get());
    }
    return delegate.validate(transaction, baseFee, blobBaseFee, transactionValidationParams);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {
    return delegate.validateForSender(transaction, sender, validationParams);
  }

  private Optional<String> additionalValidation(final Transaction transaction) {
    for (var rule : additionalValidationRules) {
      var maybeInvalidReason = rule.validate(transaction);
      if (maybeInvalidReason.isPresent()) {
        return maybeInvalidReason;
      }
    }
    return Optional.empty();
  }
}
