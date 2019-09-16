/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;

/** Validates transaction based on some criteria. */
public interface TransactionValidator {

  /**
   * Asserts whether a transaction is valid.
   *
   * @param transaction the transaction to validate
   * @return An empty @{link Optional} if the transaction is considered valid; otherwise an @{code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  ValidationResult<TransactionInvalidReason> validate(Transaction transaction);

  /**
   * Asserts whether a transaction is valid for the sender accounts current state.
   *
   * <p>Note: {@code validate} should be called before getting the sender {@link Account} used in
   * this method to ensure that a sender can be extracted from the {@link Transaction}.
   *
   * @param transaction the transaction to validateMessageFrame.State.COMPLETED_FAILED
   * @param sender the sender account state to validate against
   * @param allowFutureNonce if true, transactions with nonce equal or higher than the account nonce
   *     will be considered valid (used when received transactions in the transaction pool). If
   *     false, only a transaction with the nonce equals the account nonce will be considered valid
   *     (used when processing transactions).
   * @return An empty @{link Optional} if the transaction is considered valid; otherwise an @{code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  default ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction, final Account sender, final boolean allowFutureNonce) {
    final TransactionValidationParams validationParams =
        new TransactionValidationParams.Builder().allowFutureNonce(allowFutureNonce).build();
    return validateForSender(transaction, sender, validationParams);
  }

  ValidationResult<TransactionInvalidReason> validateForSender(
      Transaction transaction, Account sender, TransactionValidationParams validationParams);

  void setTransactionFilter(TransactionFilter transactionFilter);

  enum TransactionInvalidReason {
    WRONG_CHAIN_ID,
    REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
    INVALID_SIGNATURE,
    UPFRONT_COST_EXCEEDS_BALANCE,
    NONCE_TOO_LOW,
    INCORRECT_NONCE,
    INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
    EXCEEDS_BLOCK_GAS_LIMIT,
    TX_SENDER_NOT_AUTHORIZED,
    CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE,
    // Private Transaction Invalid Reasons
    PRIVATE_TRANSACTION_FAILED,
    PRIVATE_NONCE_TOO_LOW,
    INCORRECT_PRIVATE_NONCE,
    GAS_PRICE_TOO_LOW;
  }
}
