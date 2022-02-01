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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_SIGNATURE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_VALUE_NOT_ZERO;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.WRONG_CHAIN_ID;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.PrivateTransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateTransactionValidatorTest {

  private static final KeyPair senderKeys =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private PrivateTransactionValidator validator;

  @Before
  public void before() {
    validator = new PrivateTransactionValidator(Optional.empty());
  }

  @Test
  public void transactionWithNonceLowerThanAccountNonceShouldAlwaysReturnLowNonceError() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 2L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));

    validationResult = validator.validate(privateTransactionWithNonce(1L), 2L, true);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void
      transactionWithNonceGreaterThanAccountNonceShouldReturnIncorrectNonceErrorWhenFutureNoncesNotAllowed() {
    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(3L), 2L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  @Test
  public void
      transactionWithNonceGreaterThanAccountNonceShouldReturnValidTransactionWhenFutureNoncesAllowed() {
    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(3L), 2L, true);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
  }

  @Test
  public void
      transactionWithNonceMatchingThanAccountNonceShouldAlwaysReturnValidTransactionResult() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 1L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());

    validationResult = validator.validate(privateTransactionWithNonce(1L), 1L, true);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
  }

  @Test
  public void transactionWithInvalidChainIdShouldReturnWrongChainId() {
    validator = new PrivateTransactionValidator(Optional.of(BigInteger.ONE));

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithChainId(999), 0L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(WRONG_CHAIN_ID));
  }

  @Test
  public void
      transactionWithoutChainIdWithValidatorUsingChainIdShouldReturnReplayProtectedSignaturesNotSupported() {
    validator = new PrivateTransactionValidator(Optional.empty());

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithChainId(999), 0L, false);

    assertThat(validationResult)
        .isEqualTo(ValidationResult.invalid(REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void transactionWithInvalidSignatureShouldReturnInvalidSignature() {
    final PrivateTransaction transactionWithInvalidSignature =
        Mockito.spy(privateTransactionWithNonce(1L));
    when(transactionWithInvalidSignature.getSender()).thenThrow(new IllegalArgumentException());

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(transactionWithInvalidSignature, 1L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INVALID_SIGNATURE));
  }

  @Test
  public void transactionWithNonZeroValueShouldReturnValueNotZeroError() {
    validator = new PrivateTransactionValidator(Optional.of(BigInteger.ONE));

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithValue(1L), 0L, false);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_VALUE_NOT_ZERO));
  }

  private PrivateTransaction privateTransactionWithNonce(final long nonce) {
    return new PrivateTransactionTestFixture()
        .nonce(nonce)
        .chainId(Optional.empty())
        .createTransaction(senderKeys);
  }

  private PrivateTransaction privateTransactionWithChainId(final int chainId) {
    return new PrivateTransactionTestFixture()
        .chainId(Optional.of(BigInteger.valueOf(chainId)))
        .createTransaction(senderKeys);
  }

  private PrivateTransaction privateTransactionWithValue(final long value) {
    return new PrivateTransactionTestFixture().value(Wei.of(value)).createTransaction(senderKeys);
  }
}
