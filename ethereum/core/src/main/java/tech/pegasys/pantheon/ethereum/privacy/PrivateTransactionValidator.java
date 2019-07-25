/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.privacy;

import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INVALID_SIGNATURE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.WRONG_CHAIN_ID;

import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateTransactionValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<BigInteger> chainId;

  public PrivateTransactionValidator(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
  }

  public ValidationResult<TransactionInvalidReason> validate(
      final PrivateTransaction transaction, final Long accountNonce) {

    LOG.debug("Validating private transaction {} signature ", transaction.hash());

    ValidationResult<TransactionInvalidReason> signatureValidationResult =
        validateTransactionSignature(transaction);
    if (!signatureValidationResult.isValid()) {
      return signatureValidationResult;
    }

    final long transactionNonce = transaction.getNonce();

    LOG.debug("Validating actual nonce {} with expected nonce {}", transactionNonce, accountNonce);

    if (accountNonce > transactionNonce) {
      return ValidationResult.invalid(
          PRIVATE_NONCE_TOO_LOW,
          String.format(
              "private transaction nonce %s does not match sender account nonce %s.",
              transactionNonce, accountNonce));
    }

    if (accountNonce != transactionNonce) {
      return ValidationResult.invalid(
          INCORRECT_PRIVATE_NONCE,
          String.format(
              "private transaction nonce %s does not match sender account nonce %s.",
              transactionNonce, accountNonce));
    }

    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateTransactionSignature(
      final PrivateTransaction transaction) {
    if (chainId.isPresent()
        && (transaction.getChainId().isPresent() && !transaction.getChainId().equals(chainId))) {
      return ValidationResult.invalid(
          WRONG_CHAIN_ID,
          String.format(
              "transaction was meant for chain id %s and not this chain id %s",
              transaction.getChainId().get(), chainId.get()));
    }

    if (!chainId.isPresent() && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "replay protected signatures is not supported");
    }

    // org.bouncycastle.math.ec.ECCurve.AbstractFp.decompressPoint throws an
    // IllegalArgumentException for "Invalid point compression" for bad signatures.
    try {
      transaction.getSender();
    } catch (final IllegalArgumentException e) {
      return ValidationResult.invalid(
          INVALID_SIGNATURE, "sender could not be extracted from transaction signature");
    }

    return ValidationResult.valid();
  }
}
