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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

public interface TransactionValidator {

  /**
   * Asserts whether a transaction is valid.
   *
   * @param transaction the transaction to validate
   * @param baseFee optional baseFee
   * @param transactionValidationParams Validation parameters that will be used
   * @return the result of the validation, in case of invalid transaction the invalid reason is
   *     present
   */
  ValidationResult<TransactionInvalidReason> validate(
      Transaction transaction,
      Optional<Wei> baseFee,
      Optional<Wei> blobBaseFee,
      TransactionValidationParams transactionValidationParams);

  /**
   * Asserts whether a transaction is valid for the sender account's current state.
   *
   * <p>Note: {@code validate} should be called before getting the sender {@link Account} used in
   * this method to ensure that a sender can be extracted from the {@link Transaction}.
   *
   * @param transaction the transaction to validate
   * @param sender the account of the sender of the transaction
   * @param validationParams to customize the validation according to different scenarios, like
   *     processing block, adding to the txpool, etc...
   * @return the result of the validation, in case of invalid transaction the invalid reason is
   *     present
   */
  ValidationResult<TransactionInvalidReason> validateForSender(
      Transaction transaction, Account sender, TransactionValidationParams validationParams);
}
