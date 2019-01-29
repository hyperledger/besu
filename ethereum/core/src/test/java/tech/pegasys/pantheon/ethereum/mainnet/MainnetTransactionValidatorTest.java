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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_NONCE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.NONCE_TOO_LOW;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.WRONG_CHAIN_ID;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionTestFixture;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;

import java.util.OptionalLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionValidatorTest {

  private static final KeyPair senderKeys = KeyPair.generate();

  @Mock private GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture().chainId(1).createTransaction(senderKeys);

  @Test
  public void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false);
    final Transaction transaction =
        new TransactionTestFixture().gasLimit(10).chainId(0).createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCost(transaction)).thenReturn(Gas.of(50));

    assertThat(validator.validate(transaction))
        .isEqualTo(ValidationResult.invalid(INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false);
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(ValidationResult.invalid(REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasIncorrectChainId() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 2);
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(ValidationResult.invalid(WRONG_CHAIN_ID));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);
    assertThat(validator.validateForSender(basicTransaction, null, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(NONCE_TOO_LOW));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceAboveAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(INCORRECT_NONCE));
  }

  @Test
  public void shouldAcceptTransactionWhenNonceBetweenAccountNonceAndMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(10).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(15)))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldAcceptTransactionWhenNonceEqualsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(10).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(10)))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(10)))
        .isEqualTo(ValidationResult.invalid(INCORRECT_NONCE));
  }

  @Test
  public void transactionWithNullSenderCanBeValidIfGasPriceAndValueIsZero() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final KeyPair senderKeyPair = KeyPair.generate();
    final Address arbitrarySender = Address.fromHexString("1");
    builder.gasPrice(Wei.ZERO).nonce(0).sender(arbitrarySender).value(Wei.ZERO);

    assertThat(
            validator.validateForSender(
                builder.createTransaction(senderKeyPair), null, OptionalLong.of(10)))
        .isEqualTo(ValidationResult.valid());
  }

  private Account accountWithNonce(final long nonce) {
    return account(basicTransaction.getUpfrontCost(), nonce);
  }

  private Account accountWithBalance(final Wei balance) {
    return account(balance, basicTransaction.getNonce());
  }

  private Account account(final Wei balance, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getBalance()).thenReturn(balance);
    when(account.getNonce()).thenReturn(nonce);
    return account;
  }
}
