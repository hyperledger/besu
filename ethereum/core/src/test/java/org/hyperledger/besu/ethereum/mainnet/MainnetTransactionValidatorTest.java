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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionValidatorTest {

  private static final KeyPair senderKeys = KeyPair.generate();

  @Mock private GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture()
          .chainId(Optional.of(BigInteger.ONE))
          .createTransaction(senderKeys);

  @Test
  public void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    final Transaction transaction =
        new TransactionTestFixture()
            .gasLimit(10)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCost(transaction)).thenReturn(Gas.of(50));

    assertThat(validator.validate(transaction))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason
                    .REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasIncorrectChainId() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.valueOf(2)));
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(
            ValidationResult.invalid(TransactionValidator.TransactionInvalidReason.WRONG_CHAIN_ID));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));
    assertThat(validator.validateForSender(basicTransaction, null, false))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(
            ValidationResult.invalid(TransactionValidator.TransactionInvalidReason.NONCE_TOO_LOW));
  }

  @Test
  public void
      shouldRejectTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsNotAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.INCORRECT_NONCE));
  }

  @Test
  public void
      shouldAcceptTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, false))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.INCORRECT_NONCE));
  }

  @Test
  public void transactionWithNullSenderCanBeValidIfGasPriceAndValueIsZero() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.of(BigInteger.ONE));

    final TransactionTestFixture builder = new TransactionTestFixture();
    final KeyPair senderKeyPair = KeyPair.generate();
    final Address arbitrarySender = Address.fromHexString("1");
    builder.gasPrice(Wei.ZERO).nonce(0).sender(arbitrarySender).value(Wei.ZERO);

    assertThat(validator.validateForSender(builder.createTransaction(senderKeyPair), null, false))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionIfAccountIsNotPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    validator.setTransactionFilter(transactionFilter(false));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED));
  }

  @Test
  public void shouldAcceptValidTransactionIfAccountIsPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    validator.setTransactionFilter(transactionFilter(true));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldPropagateCorrectStateChangeParamToTransactionFilter() {
    final ArgumentCaptor<Boolean> stateChangeParamCaptor = ArgumentCaptor.forClass(Boolean.class);
    final TransactionFilter transactionFilter = mock(TransactionFilter.class);
    when(transactionFilter.permitted(any(Transaction.class), stateChangeParamCaptor.capture()))
        .thenReturn(true);

    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    validator.setTransactionFilter(transactionFilter);

    final TransactionValidationParams validationParams =
        new TransactionValidationParams.Builder().checkOnchainPermissions(true).build();

    validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams);

    assertThat(stateChangeParamCaptor.getValue()).isTrue();
  }

  @Test
  public void shouldNotCheckAccountPermissionIfBothValidationParamsCheckPermissionsAreFalse() {
    final TransactionFilter transactionFilter = mock(TransactionFilter.class);

    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty());
    validator.setTransactionFilter(transactionFilter);

    final TransactionValidationParams validationParams =
        new TransactionValidationParams.Builder()
            .checkOnchainPermissions(false)
            .checkLocalPermissions(false)
            .build();

    validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams);

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams))
        .isEqualTo(ValidationResult.valid());

    verifyZeroInteractions(transactionFilter);
  }

  private Account accountWithNonce(final long nonce) {
    return account(basicTransaction.getUpfrontCost(), nonce);
  }

  private Account account(final Wei balance, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getBalance()).thenReturn(balance);
    when(account.getNonce()).thenReturn(nonce);
    return account;
  }

  private TransactionFilter transactionFilter(final boolean permitted) {
    final TransactionFilter transactionFilter = mock(TransactionFilter.class);
    when(transactionFilter.permitted(any(Transaction.class), anyBoolean())).thenReturn(permitted);
    return transactionFilter;
  }
}
