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
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;

import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class PrivateTransactionValidator {

  private static final Logger LOG = LogManager.getLogger();

  public ValidationResult<TransactionInvalidReason> validate(
      final PrivateTransaction transaction, final Long accountNonce) {
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
}
