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
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_TRANSACTION_FORMAT;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
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

  private static final TransactionValidationParams transactionValidationParams =
      TransactionValidationParams.processingBlockParams;

  @Mock private GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture()
          .chainId(Optional.of(BigInteger.ONE))
          .createTransaction(senderKeys);

  private final boolean defaultGoQuorumCompatibilityMode = false;

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
    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(50L);

    assertThat(validator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(
            ValidationResult.invalid(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    assertThat(validator.validate(basicTransaction, Optional.empty(), transactionValidationParams))
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
    assertThat(validator.validate(basicTransaction, Optional.empty(), transactionValidationParams))
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
  public void shouldRejectTransactionIfAccountIsNotEOA() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter(false));

    Account invalidEOA =
        when(account(basicTransaction.getUpfrontCost(), basicTransaction.getNonce()).getCodeHash())
            .thenReturn(Hash.fromHexStringLenient("0xdeadbeef"))
            .getMock();

    assertThat(validator.validateForSender(basicTransaction, invalidEOA, true))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED));
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
  public void shouldRejectTransactionWithMaxFeeTimesGasLimitGreaterThanBalance() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, false, Optional.empty(), defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter(true));

    assertThat(
            validator.validateForSender(
                Transaction.builder()
                    .type(TransactionType.EIP1559)
                    .nonce(0)
                    .maxPriorityFeePerGas(Wei.of(5))
                    .maxFeePerGas(Wei.of(7))
                    .gasLimit(15)
                    .to(Address.ZERO)
                    .value(Wei.of(0))
                    .payload(Bytes.EMPTY)
                    .chainId(BigInteger.ONE)
                    .signAndBuild(new SECP256K1().generateKeyPair()),
                account(Wei.of(100), 0),
                true))
        .isEqualTo(ValidationResult.invalid(UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWithMaxPriorityFeeGreaterThanMaxFee() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.values()),
            defaultGoQuorumCompatibilityMode);
    validator.setTransactionFilter(transactionFilter(true));

    final Transaction transaction =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(7))
            .maxFeePerGas(Wei.of(4))
            .gasLimit(15)
            .to(Address.ZERO)
            .value(Wei.of(0))
            .payload(Bytes.EMPTY)
            .chainId(BigInteger.ONE)
            .signAndBuild(new SECP256K1().generateKeyPair());

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(transaction, Optional.of(Wei.ONE), transactionValidationParams);
    assertThat(validationResult)
        .isEqualTo(ValidationResult.invalid(MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS));
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("max priority fee per gas cannot be greater than max fee per gas");
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

    verifyNoInteractions(transactionFilter);
  }

  @Test
  public void shouldAcceptOnlyTransactionsInAcceptedTransactionTypes() {
    final MainnetTransactionValidator frontierValidator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.legacy(),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER),
            defaultGoQuorumCompatibilityMode);

    final MainnetTransactionValidator eip1559Validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxPriorityFeePerGas(Optional.of(Wei.of(3)))
            .maxFeePerGas(Optional.of(Wei.of(6)))
            .gasLimit(21000)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);

    assertThat(
            frontierValidator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(INVALID_TRANSACTION_FORMAT));

    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(0L);

    assertThat(
            eip1559Validator.validate(
                transaction, Optional.of(Wei.ONE), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionIfEIP1559TransactionGasPriceLessBaseFee() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(1)))
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    final Optional<Wei> basefee = Optional.of(Wei.of(150000L));
    assertThat(validator.validate(transaction, basefee, transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(INVALID_TRANSACTION_FORMAT));
  }

  @Test
  public void shouldAcceptZeroGasPriceTransactionIfBaseFeeIsZero() {
    final Optional<Wei> zeroBaseFee = Optional.of(Wei.ZERO);
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L, zeroBaseFee),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxPriorityFeePerGas(Optional.of(Wei.ZERO))
            .maxFeePerGas(Optional.of(Wei.ZERO))
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);

    assertThat(validator.validate(transaction, zeroBaseFee, transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldAcceptValidEIP1559() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(150000L)))
            .type(TransactionType.EIP1559)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    final Optional<Wei> basefee = Optional.of(Wei.of(150000L));
    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(50L);

    assertThat(validator.validate(transaction, basefee, transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldValidate1559TransactionWithPriceLowerThanBaseFeeForTransactionPool() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            defaultGoQuorumCompatibilityMode);
    final Transaction transaction =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(1)))
            .type(TransactionType.EIP1559)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(50L);

    assertThat(
            validator.validate(
                transaction, Optional.of(Wei.ONE), TransactionValidationParams.transactionPool()))
        .isEqualTo(ValidationResult.valid());
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

    assertThat(
            validator
                .validate(transaction, Optional.empty(), transactionValidationParams)
                .isValid())
        .isFalse();
    assertThat(
            validator
                .validate(transaction, Optional.empty(), transactionValidationParams)
                .getInvalidReason())
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

    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(50L);

    assertThat(
            validator
                .validate(transaction, Optional.empty(), transactionValidationParams)
                .isValid())
        .isTrue();
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
