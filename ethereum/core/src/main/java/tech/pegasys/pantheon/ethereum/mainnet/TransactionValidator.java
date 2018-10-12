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
   * @param transaction the transaction to validate
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
    EXCEEDS_BLOCK_GAS_LIMIT
  }
}
