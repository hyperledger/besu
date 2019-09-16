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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INVALID_SIGNATURE;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.WRONG_CHAIN_ID;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.PrivateTransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PrivateTransactionValidatorTest {

  private static final KeyPair senderKeys = KeyPair.generate();

  private PrivateTransactionValidator validator;

  @Before
  public void before() {
    validator = new PrivateTransactionValidator(Optional.empty());
  }

  @Test
  public void transactionWithNonceLowerThanAccountNonceShouldReturnLowNonceError() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 2L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void transactionWithNonceGreaterThanAccountNonceShouldReturnIncorrectNonceError() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(3L), 2L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  @Test
  public void transactionWithNonceMatchingThanAccountNonceShouldReturnValidTransactionResult() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 1L);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
  }

  @Test
  public void transactionWithInvalidChainIdShouldReturnWrongChainId() {
    validator = new PrivateTransactionValidator(Optional.of(BigInteger.ONE));

    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithChainId(999), 0L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(WRONG_CHAIN_ID));
  }

  @Test
  public void
      transactionWithoutChainIdWithValidatorUsingChainIdShouldReturnReplayProtectedSignaturesNotSupported() {
    validator = new PrivateTransactionValidator(Optional.empty());

    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithChainId(999), 0L);

    assertThat(validationResult)
        .isEqualTo(ValidationResult.invalid(REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void transactionWithInvalidSignatureShouldReturnInvalidSignature() {
    PrivateTransaction transactionWithInvalidSignature =
        Mockito.spy(privateTransactionWithNonce(1L));
    when(transactionWithInvalidSignature.getSender()).thenThrow(new IllegalArgumentException());

    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(transactionWithInvalidSignature, 1L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INVALID_SIGNATURE));
  }

  private PrivateTransaction privateTransactionWithNonce(final long nonce) {
    PrivateTransactionTestFixture privateTransactionTestFixture =
        new PrivateTransactionTestFixture();
    privateTransactionTestFixture.nonce(nonce);
    privateTransactionTestFixture.chainId(Optional.empty());
    return privateTransactionTestFixture.createTransaction(senderKeys);
  }

  private PrivateTransaction privateTransactionWithChainId(final int chainId) {
    PrivateTransactionTestFixture privateTransactionTestFixture =
        new PrivateTransactionTestFixture();
    privateTransactionTestFixture.chainId(Optional.of(BigInteger.valueOf(chainId)));
    return privateTransactionTestFixture.createTransaction(senderKeys);
  }
}
