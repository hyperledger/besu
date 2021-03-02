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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.GAS_PRICE_MUST_BE_ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.GasAndAccessedState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionValidatorTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair senderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();

  @Mock private GasCalculator gasCalculator;

  @Mock private TransactionPriceCalculator transactionPriceCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture()
          .chainId(Optional.of(BigInteger.ONE))
          .createTransaction(senderKeys);

  private final boolean defaultGoQuorumCompatibilityMode = false;

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .gasLimit(10)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction))
        .thenReturn(new GasAndAccessedState(Gas.of(50)));

    assertThat(validator.validate(transaction, Optional.empty()))
        .isEqualTo(
            ValidationResult.invalid(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    assertThat(validator.validate(basicTransaction, Optional.empty()))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasIncorrectChainId() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            false,
            Optional.of(BigInteger.valueOf(2)),
            defaultGoQuorumCompatibilityMode);
    assertThat(validator.validate(basicTransaction, Optional.empty()))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.WRONG_CHAIN_ID));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);
    assertThat(validator.validateForSender(basicTransaction, null, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
  }

  @Test
  public void
      shouldRejectTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsNotAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.INCORRECT_NONCE));
  }

  @Test
  public void
      shouldAcceptTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.INCORRECT_NONCE));
  }

  @Test
  public void transactionWithNullSenderCanBeValidIfGasPriceAndValueIsZero() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.of(BigInteger.ONE), defaultGoQuorumCompatibilityMode);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final KeyPair senderKeyPair = SIGNATURE_ALGORITHM.get().generateKeyPair();
    final Address arbitrarySender = Address.fromHexString("1");
    builder.gasPrice(Wei.ZERO).nonce(0).sender(arbitrarySender).value(Wei.ZERO);

    assertThat(validator.validateForSender(builder.createTransaction(senderKeyPair), null, false))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionIfAccountIsNotPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter(false));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED));
  }

  @Test
  public void shouldAcceptValidTransactionIfAccountIsPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter(true));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldPropagateCorrectStateChangeParamToTransactionFilter() {
    final ArgumentCaptor<Boolean> stateChangeLocalParamCaptor =
        ArgumentCaptor.forClass(Boolean.class);
    final ArgumentCaptor<Boolean> stateChangeOnchainParamCaptor =
        ArgumentCaptor.forClass(Boolean.class);
    final TransactionFilter transactionFilter = mock(TransactionFilter.class);
    when(transactionFilter.permitted(
            any(Transaction.class),
            stateChangeLocalParamCaptor.capture(),
            stateChangeOnchainParamCaptor.capture()))
        .thenReturn(true);

    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter);

    final TransactionValidationParams validationParams =
        ImmutableTransactionValidationParams.builder().checkOnchainPermissions(true).build();

    validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams);

    assertThat(stateChangeLocalParamCaptor.getValue()).isTrue();
    assertThat(stateChangeOnchainParamCaptor.getValue()).isTrue();
  }

  @Test
  public void shouldNotCheckAccountPermissionIfBothValidationParamsCheckPermissionsAreFalse() {
    final TransactionFilter transactionFilter = mock(TransactionFilter.class);

    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter);

    final TransactionValidationParams validationParams =
        ImmutableTransactionValidationParams.builder()
            .checkOnchainPermissions(false)
            .checkLocalPermissions(false)
            .build();

    validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams);

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), validationParams))
        .isEqualTo(ValidationResult.valid());

    verifyZeroInteractions(transactionFilter);
  }

  @Test
  public void shouldAcceptOnlyTransactionsInAcceptedTransactionTypes() {
    ExperimentalEIPs.eip1559Enabled = true;

    final MainnetTransactionValidator frontierValidator =
        new MainnetTransactionValidator(
            gasCalculator,
            Optional.of(transactionPriceCalculator),
            false,
            Optional.empty(),
            Set.of(TransactionType.FRONTIER),
            defaultGoQuorumCompatibilityMode);

    final MainnetTransactionValidator eip1559Validator =
        new MainnetTransactionValidator(
            gasCalculator,
            Optional.of(transactionPriceCalculator),
            false,
            Optional.empty(),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .gasPremium(Optional.of(Wei.of(3)))
            .feeCap(Optional.of(Wei.of(6)))
            .gasLimit(21000)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);

    when(transactionPriceCalculator.price(eq(transaction), any())).thenReturn(Wei.of(160000L));

    assertThat(frontierValidator.validate(transaction, Optional.empty()))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.INVALID_TRANSACTION_FORMAT));

    when(gasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction))
        .thenReturn(new GasAndAccessedState(Gas.of(0)));

    assertThat(eip1559Validator.validate(transaction, Optional.of(1L)))
        .isEqualTo(ValidationResult.valid());

    ExperimentalEIPs.eip1559Enabled = false;
  }

  @Test
  public void shouldRejectTransactionIfEIP1559TransactionGasPriceLessBaseFee() {
    ExperimentalEIPs.eip1559Enabled = true;
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            Optional.of(transactionPriceCalculator),
            false,
            Optional.empty(),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .gasPremium(Optional.of(Wei.of(1)))
            .feeCap(Optional.of(Wei.of(1)))
            .chainId(Optional.empty())
            .createTransaction(senderKeys);
    final Optional<Long> basefee = Optional.of(150000L);
    when(transactionPriceCalculator.price(transaction, basefee)).thenReturn(Wei.of(1));
    assertThat(validator.validate(transaction, basefee))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.INVALID_TRANSACTION_FORMAT));
    ExperimentalEIPs.eip1559Enabled = false;
  }

  @Test
  public void shouldAcceptValidEIP1559() {
    ExperimentalEIPs.eip1559Enabled = true;
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            Optional.of(transactionPriceCalculator),
            false,
            Optional.empty(),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .gasPremium(Optional.of(Wei.of(1)))
            .feeCap(Optional.of(Wei.of(1)))
            .chainId(Optional.empty())
            .createTransaction(senderKeys);
    final Optional<Long> basefee = Optional.of(150000L);
    when(gasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction))
        .thenReturn(new GasAndAccessedState(Gas.of(50)));

    assertThat(validator.validate(transaction, basefee)).isEqualTo(ValidationResult.valid());
    ExperimentalEIPs.eip1559Enabled = false;
  }

  @Test
  public void goQuorumCompatibilityModeRejectNonZeroGasPrice() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty(), true);
    final Transaction transaction =
        new TransactionTestFixture()
            .gasPrice(Wei.ONE)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);

    assertThat(validator.validate(transaction, Optional.empty()).isValid()).isFalse();
    assertThat(validator.validate(transaction, Optional.empty()).getInvalidReason())
        .isEqualTo(GAS_PRICE_MUST_BE_ZERO);
  }

  @Test
  public void goQuorumCompatibilityModeSuccessZeroGasPrice() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, Optional.empty(), true);
    final Transaction transaction =
        new TransactionTestFixture()
            .gasPrice(Wei.ZERO)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);

    when(gasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction))
        .thenReturn(new GasAndAccessedState(Gas.of(50)));

    assertThat(validator.validate(transaction, Optional.empty()).isValid()).isTrue();
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
    when(transactionFilter.permitted(any(Transaction.class), anyBoolean(), anyBoolean()))
        .thenReturn(permitted);
    return transactionFilter;
  }
}
