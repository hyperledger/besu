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
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE;
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
import org.hyperledger.besu.ethereum.GasLimitCalculator;
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
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MainnetTransactionValidatorTest {

  protected static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair senderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();

  private static final TransactionValidationParams transactionValidationParams =
      TransactionValidationParams.processingBlockParams;

  @Mock protected GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture()
          .chainId(Optional.of(BigInteger.ONE))
          .createTransaction(senderKeys);

  @Test
  public void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
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
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
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
            GasLimitCalculator.constant(),
            false,
            Optional.of(BigInteger.valueOf(2)));
    assertThat(validator.validate(basicTransaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.WRONG_CHAIN_ID));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));
    assertThat(validator.validateForSender(basicTransaction, null, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
  }

  @Test
  public void
      shouldRejectTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsNotAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH));
  }

  @Test
  public void
      shouldAcceptTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsAllowed() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, false))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH));
  }

  @Test
  public void transactionWithNullSenderCanBeValidIfGasPriceAndValueIsZero() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

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
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());

    Account invalidEOA =
        when(account(basicTransaction.getUpfrontCost(0L), basicTransaction.getNonce())
                .getCodeHash())
            .thenReturn(Hash.fromHexStringLenient("0xdeadbeef"))
            .getMock();

    assertThat(validator.validateForSender(basicTransaction, invalidEOA, true))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED))
        .extracting(ValidationResult::getErrorMessage)
        .isEqualTo(
            "Sender "
                + basicTransaction.getSender()
                + " has deployed code and so is not authorized to send transactions");
  }

  @Test
  public void shouldRejectTransactionIfAccountIsNotPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
    validator.setTransactionFilter(transactionFilter(false));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED));
  }

  @Test
  public void shouldAcceptValidTransactionIfAccountIsPermitted() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
    validator.setTransactionFilter(transactionFilter(true));

    assertThat(validator.validateForSender(basicTransaction, accountWithNonce(0), true))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWithMaxFeeTimesGasLimitGreaterThanBalance() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());

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
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(
                new TransactionType[] {
                  TransactionType.FRONTIER, TransactionType.ACCESS_LIST, TransactionType.EIP1559
                }),
            Integer.MAX_VALUE);

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
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
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
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
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
            GasLimitCalculator.constant(),
            FeeMarket.legacy(),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER),
            Integer.MAX_VALUE);

    final MainnetTransactionValidator eip1559Validator =
        new MainnetTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            Integer.MAX_VALUE);

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
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            Integer.MAX_VALUE);
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(1)))
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    final Optional<Wei> basefee = Optional.of(Wei.of(150000L));
    assertThat(validator.validate(transaction, basefee, transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(GAS_PRICE_BELOW_CURRENT_BASE_FEE));
  }

  @Test
  public void shouldAcceptZeroGasPriceTransactionIfBaseFeeIsZero() {
    final Optional<Wei> zeroBaseFee = Optional.of(Wei.ZERO);
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.london(0L, zeroBaseFee),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            Integer.MAX_VALUE);
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
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            Integer.MAX_VALUE);
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
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            Integer.MAX_VALUE);
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
  public void shouldRejectTooLargeInitcode() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559),
            0xc000);

    var bigPayload =
        new TransactionTestFixture()
            .payload(Bytes.fromHexString("0x" + "00".repeat(0xc001)))
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    var validationResult =
        validator.validate(bigPayload, Optional.empty(), transactionValidationParams);

    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.INITCODE_TOO_LARGE);
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Initcode size of 49153 exceeds maximum size of 49152");
  }

  @Test
  public void shouldAcceptTransactionWithAtLeastOneBlob() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.london(0L),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559, TransactionType.BLOB),
            0xc000);

    var blobTx =
        new TransactionTestFixture()
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(senderKeys);
    var validationResult =
        validator.validate(blobTx, Optional.empty(), transactionValidationParams);

    assertThat(validationResult.isValid()).isTrue();
  }

  private Account accountWithNonce(final long nonce) {
    return account(basicTransaction.getUpfrontCost(0L), nonce);
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
