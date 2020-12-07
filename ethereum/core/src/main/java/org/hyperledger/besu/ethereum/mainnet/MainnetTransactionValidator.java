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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Validates a transaction based on Frontier protocol runtime requirements.
 *
 * <p>The {@link MainnetTransactionValidator} performs the intrinsic gas cost check on the given
 * {@link Transaction}.
 */
public class MainnetTransactionValidator {

  private final GasCalculator gasCalculator;
  private final Optional<TransactionPriceCalculator> transactionPriceCalculator;

  private final boolean disallowSignatureMalleability;

  private final Optional<BigInteger> chainId;

  private Optional<TransactionFilter> transactionFilter = Optional.empty();
  private final Optional<EIP1559> maybeEip1559;
  private final AcceptedTransactionTypes acceptedTransactionTypes;
  private final boolean goQuorumCompatibilityMode;

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final boolean goQuorumCompatibilityMode) {
    this(
        gasCalculator,
        Optional.empty(),
        checkSignatureMalleability,
        chainId,
        Optional.empty(),
        AcceptedTransactionTypes.FRONTIER_TRANSACTIONS,
        goQuorumCompatibilityMode);
  }

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final Optional<TransactionPriceCalculator> transactionPriceCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Optional<EIP1559> maybeEip1559,
      final AcceptedTransactionTypes acceptedTransactionTypes,
      final boolean goQuorumCompatibilityMode) {
    this.gasCalculator = gasCalculator;
    this.transactionPriceCalculator = transactionPriceCalculator;
    this.disallowSignatureMalleability = checkSignatureMalleability;
    this.chainId = chainId;
    this.maybeEip1559 = maybeEip1559;
    this.acceptedTransactionTypes = acceptedTransactionTypes;
    this.goQuorumCompatibilityMode = goQuorumCompatibilityMode;
  }

  /**
   * Asserts whether a transaction is valid.
   *
   * @param transaction the transaction to validate
   * @param baseFee optional baseFee
   * @return An empty @{link Optional} if the transaction is considered valid; otherwise an @{code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction, final Optional<Long> baseFee) {
    final ValidationResult<TransactionInvalidReason> signatureResult =
        validateTransactionSignature(transaction);
    if (!signatureResult.isValid()) {
      return signatureResult;
    }

    if (goQuorumCompatibilityMode && !transaction.getGasPrice().isZero()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.GAS_PRICE_MUST_BE_ZERO,
          "gasPrice must be set to zero on a GoQuorum compatible network");
    }

    if (ExperimentalEIPs.eip1559Enabled && maybeEip1559.isPresent()) {
      final EIP1559 eip1559 = maybeEip1559.get();
      if (!eip1559.isValidFormat(transaction, acceptedTransactionTypes)) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
            String.format(
                "transaction format is invalid, accepted transaction types are %s",
                acceptedTransactionTypes.toString()));
      }
      if (transaction.isEIP1559Transaction()) {
        final Wei price = transactionPriceCalculator.orElseThrow().price(transaction, baseFee);
        if (price.compareTo(Wei.of(baseFee.orElseThrow())) < 0) {
          return ValidationResult.invalid(
              TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
              String.format("gasPrice is less than the current BaseFee"));
        }
      }
    } else if (transaction.isEIP1559Transaction()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          String.format(
              "transaction format is invalid, accepted transaction types are %s",
              acceptedTransactionTypes.toString()));
    }

    final Gas intrinsicGasCost = gasCalculator.transactionIntrinsicGasCost(transaction);
    if (intrinsicGasCost.compareTo(Gas.of(transaction.getGasLimit())) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
          String.format(
              "intrinsic gas cost %s exceeds gas limit %s",
              intrinsicGasCost, transaction.getGasLimit()));
    }

    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {
    Wei senderBalance = Account.DEFAULT_BALANCE;
    long senderNonce = Account.DEFAULT_NONCE;

    if (sender != null) {
      senderBalance = sender.getBalance();
      senderNonce = sender.getNonce();
    }

    if (transaction.getUpfrontCost().compareTo(senderBalance) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
          String.format(
              "transaction up-front cost %s exceeds transaction sender account balance %s",
              transaction.getUpfrontCost(), senderBalance));
    }

    if (transaction.getNonce() < senderNonce) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_LOW,
          String.format(
              "transaction nonce %s below sender account nonce %s",
              transaction.getNonce(), senderNonce));
    }

    if (!validationParams.isAllowFutureNonce() && senderNonce != transaction.getNonce()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INCORRECT_NONCE,
          String.format(
              "transaction nonce %s does not match sender account nonce %s.",
              transaction.getNonce(), senderNonce));
    }

    if (!isSenderAllowed(transaction, validationParams)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format("Sender %s is not on the Account Allowlist", transaction.getSender()));
    }

    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateTransactionSignature(
      final Transaction transaction) {
    if (chainId.isPresent()
        && (transaction.getChainId().isPresent() && !transaction.getChainId().equals(chainId))) {
      return ValidationResult.invalid(
          TransactionInvalidReason.WRONG_CHAIN_ID,
          String.format(
              "transaction was meant for chain id %s and not this chain id %s",
              transaction.getChainId().get(), chainId.get()));
    }

    if (!chainId.isPresent() && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "replay protected signatures is not supported");
    }

    final SECP256K1.Signature signature = transaction.getSignature();
    if (disallowSignatureMalleability
        && signature.getS().compareTo(SECP256K1.HALF_CURVE_ORDER) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          String.format(
              "Signature s value should be less than %s, but got %s",
              SECP256K1.HALF_CURVE_ORDER, signature.getS()));
    }

    // org.bouncycastle.math.ec.ECCurve.AbstractFp.decompressPoint throws an
    // IllegalArgumentException for "Invalid point compression" for bad signatures.
    try {
      transaction.getSender();
    } catch (final IllegalArgumentException e) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          "sender could not be extracted from transaction signature");
    }

    return ValidationResult.valid();
  }

  private boolean isSenderAllowed(
      final Transaction transaction, final TransactionValidationParams validationParams) {
    if (validationParams.checkLocalPermissions() || validationParams.checkOnchainPermissions()) {
      return transactionFilter
          .map(
              c ->
                  c.permitted(
                      transaction,
                      validationParams.checkLocalPermissions(),
                      validationParams.checkOnchainPermissions()))
          .orElse(true);
    } else {
      return true;
    }
  }

  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    this.transactionFilter = Optional.of(transactionFilter);
  }

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
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction, final Account sender, final boolean allowFutureNonce) {
    final TransactionValidationParams validationParams =
        ImmutableTransactionValidationParams.builder().isAllowFutureNonce(allowFutureNonce).build();
    return validateForSender(transaction, sender, validationParams);
  }
}
