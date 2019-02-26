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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Transaction;

import java.util.OptionalLong;

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
   * @param maximumNonce the maximum transaction nonce. If not provided the transaction nonce must
   *     equal the sender's current account nonce
   * @return An empty @{link Optional} if the transaction is considered valid; otherwise an @{code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  ValidationResult<TransactionInvalidReason> validateForSender(
      Transaction transaction, Account sender, OptionalLong maximumNonce);

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
    PRIVATE_TRANSACTION_FAILED
  }
}
