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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateTransactionValidator {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateTransactionValidator.class);

  private final Optional<BigInteger> chainId;

  public PrivateTransactionValidator(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  public ValidationResult<TransactionInvalidReason> validate(
      final PrivateTransaction transaction,
      final Long accountNonce,
      final boolean allowFutureNonces) {
    LOG.debug("Validating private transaction {}", transaction);
    final ValidationResult<TransactionInvalidReason> privateFieldsValidationResult =
        validatePrivateTransactionFields(transaction);
    if (!privateFieldsValidationResult.isValid()) {
      LOG.debug(
          "Private Transaction fields are invalid {}",
          privateFieldsValidationResult.getErrorMessage());
      return privateFieldsValidationResult;
    }

    final ValidationResult<TransactionInvalidReason> signatureValidationResult =
        validateTransactionSignature(transaction);
    if (!signatureValidationResult.isValid()) {
      LOG.debug(
          "Private Transaction failed signature validation {}, {}",
          signatureValidationResult.getInvalidReason(),
          signatureValidationResult.getErrorMessage());
      return signatureValidationResult;
    }

    final long transactionNonce = transaction.getNonce();

    LOG.debug("Validating actual nonce {}, with expected nonce {}", transactionNonce, accountNonce);

    if (accountNonce > transactionNonce) {
      final String errorMessage =
          String.format(
              "Private Transaction nonce %s, is lower than sender account nonce %s.",
              transactionNonce, accountNonce);
      LOG.debug(errorMessage);
      return ValidationResult.invalid(TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW, errorMessage);
    }

    if (!allowFutureNonces && accountNonce != transactionNonce) {
      final String errorMessage =
          String.format(
              "Private Transaction nonce %s, does not match sender account nonce %s.",
              transactionNonce, accountNonce);
      LOG.debug(errorMessage);
      return ValidationResult.invalid(
          TransactionInvalidReason.INCORRECT_PRIVATE_NONCE, errorMessage);
    }

    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validatePrivateTransactionFields(
      final PrivateTransaction privateTransaction) {
    if (!privateTransaction.getValue().isZero()) {
      return ValidationResult.invalid(TransactionInvalidReason.PRIVATE_VALUE_NOT_ZERO);
    }
    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validateTransactionSignature(
      final PrivateTransaction transaction) {
    if (chainId.isPresent()
        && (transaction.getChainId().isPresent() && !transaction.getChainId().equals(chainId))) {
      return ValidationResult.invalid(
          TransactionInvalidReason.WRONG_CHAIN_ID,
          String.format(
              "Transaction was meant for chain id %s, not this chain id %s",
              transaction.getChainId().get(), chainId.get()));
    }

    if (chainId.isEmpty() && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "Replay protection (chainId) is not supported");
    }

    // org.bouncycastle.math.ec.ECCurve.AbstractFp.decompressPoint throws an
    // IllegalArgumentException for "Invalid point compression" for bad signatures.
    try {
      transaction.getSender();
    } catch (final IllegalArgumentException e) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          "Sender could not be extracted from transaction signature");
    }

    return ValidationResult.valid();
  }
}
