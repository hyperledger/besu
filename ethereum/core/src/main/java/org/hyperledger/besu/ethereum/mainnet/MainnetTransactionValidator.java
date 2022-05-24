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

import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import com.google.common.primitives.Longs;

/**
 * Validates a transaction based on Frontier protocol runtime requirements.
 *
 * <p>The {@link MainnetTransactionValidator} performs the intrinsic gas cost check on the given
 * {@link Transaction}.
 */
public class MainnetTransactionValidator {

  private final GasCalculator gasCalculator;
  private final FeeMarket feeMarket;

  private final boolean disallowSignatureMalleability;

  private final Optional<BigInteger> chainId;

  private Optional<TransactionFilter> transactionFilter = Optional.empty();
  private final Set<TransactionType> acceptedTransactionTypes;
  private final boolean goQuorumCompatibilityMode;

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final boolean goQuorumCompatibilityMode) {
    this(
        gasCalculator,
        checkSignatureMalleability,
        chainId,
        Set.of(TransactionType.FRONTIER),
        goQuorumCompatibilityMode);
  }

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final boolean quorumCompatibilityMode) {
    this(
        gasCalculator,
        FeeMarket.legacy(),
        checkSignatureMalleability,
        chainId,
        acceptedTransactionTypes,
        quorumCompatibilityMode);
  }

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final boolean goQuorumCompatibilityMode) {
    this.gasCalculator = gasCalculator;
    this.feeMarket = feeMarket;
    this.disallowSignatureMalleability = checkSignatureMalleability;
    this.chainId = chainId;
    this.acceptedTransactionTypes = acceptedTransactionTypes;
    this.goQuorumCompatibilityMode = goQuorumCompatibilityMode;
  }

  /**
   * Asserts whether a transaction is valid.
   *
   * @param transaction the transaction to validate
   * @param baseFee optional baseFee
   * @param transactionValidationParams Validation parameters that will be used
   * @return An empty {@link Optional} if the transaction is considered valid; otherwise an {@code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final TransactionValidationParams transactionValidationParams) {
    final ValidationResult<TransactionInvalidReason> signatureResult =
        validateTransactionSignature(transaction);
    if (!signatureResult.isValid()) {
      return signatureResult;
    }

    if (goQuorumCompatibilityMode && transaction.hasCostParams()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.GAS_PRICE_MUST_BE_ZERO,
          "gasPrice must be set to zero on a GoQuorum compatible network");
    }

    final TransactionType transactionType = transaction.getType();
    if (!acceptedTransactionTypes.contains(transactionType)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          String.format(
              "Transaction type %s is invalid, accepted transaction types are %s",
              transactionType, acceptedTransactionTypes));
    }

    if (baseFee.isPresent()) {
      final Wei price = feeMarket.getTransactionPriceCalculator().price(transaction, baseFee);
      if (!transactionValidationParams.isAllowMaxFeeGasBelowBaseFee()
          && price.compareTo(baseFee.orElseThrow()) < 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
            "gasPrice is less than the current BaseFee");
      }

      // assert transaction.max_fee_per_gas >= transaction.max_priority_fee_per_gas
      if (transaction.getType().supports1559FeeMarket()
          && transaction
                  .getMaxPriorityFeePerGas()
                  .get()
                  .getAsBigInteger()
                  .compareTo(transaction.getMaxFeePerGas().get().getAsBigInteger())
              > 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS,
            "max priority fee per gas cannot be greater than max fee per gas");
      }
    }

    if (transaction.getNonce() == MAX_NONCE) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_HIGH, "Nonces must be less than 2^64-1");
    }

    if (transaction
            .getGasPrice()
            .or(transaction::getMaxFeePerGas)
            .orElse(Wei.ZERO)
            .getAsBigInteger()
            .multiply(new BigInteger(1, Longs.toByteArray(transaction.getGasLimit())))
            .bitLength()
        > 256) {
      return ValidationResult.invalid(
          TransactionInvalidReason.UPFRONT_FEE_TOO_HIGH,
          "gasLimit x price must be less than 2^256");
    }

    final long intrinsicGasCost =
        gasCalculator.transactionIntrinsicGasCost(
                transaction.getPayload(), transaction.isContractCreation())
            + (transaction.getAccessList().map(gasCalculator::accessListGasCost).orElse(0L));
    if (Long.compareUnsigned(intrinsicGasCost, transaction.getGasLimit()) > 0) {
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
    Hash codeHash = Hash.EMPTY;

    if (sender != null) {
      senderBalance = sender.getBalance();
      senderNonce = sender.getNonce();
      if (sender.getCodeHash() != null) codeHash = sender.getCodeHash();
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

    if (!validationParams.isAllowContractAddressAsSender() && !codeHash.equals(Hash.EMPTY)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format(
              "Sender %s has deployed code and so is not authorized to send transactions",
              transaction.getSender()));
    }

    if (!isSenderAllowed(transaction, validationParams)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format("Sender %s is not on the Account Allowlist", transaction.getSender()));
    }

    return ValidationResult.valid();
  }

  public boolean isReplayProtectionSupported() {
    return chainId.isPresent();
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

    if (!transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)
        && chainId.isEmpty()
        && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "replay protected signatures is not supported");
    }

    final SECPSignature signature = transaction.getSignature();
    final BigInteger halfCurveOrder = SignatureAlgorithmFactory.getInstance().getHalfCurveOrder();
    if (disallowSignatureMalleability && signature.getS().compareTo(halfCurveOrder) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          String.format(
              "Signature s value should be less than %s, but got %s",
              halfCurveOrder, signature.getS()));
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
   * Asserts whether a transaction is valid for the sender account's current state.
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
   * @return An empty {@link Optional} if the transaction is considered valid; otherwise an {@code
   *     Optional} containing a {@link TransactionInvalidReason} that identifies why the transaction
   *     is invalid.
   */
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction, final Account sender, final boolean allowFutureNonce) {
    final TransactionValidationParams validationParams =
        ImmutableTransactionValidationParams.builder().isAllowFutureNonce(allowFutureNonce).build();
    return validateForSender(transaction, sender, validationParams);
  }

  public boolean getGoQuorumCompatibilityMode() {
    return goQuorumCompatibilityMode;
  }
}
