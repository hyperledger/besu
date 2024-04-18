/*
 * Copyright Hyperledger Besu contributors.
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
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.processingBlockParams;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.transactionPoolParams;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_INITCODE_LIST;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_INITCODE_TX_TARGET;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_TRANSACTION_FORMAT;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MainnetTransactionValidatorTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair senderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();

  private static final TransactionValidationParams transactionValidationParams =
      processingBlockParams;
  @Mock protected GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture()
          .nonce(30)
          .chainId(Optional.of(BigInteger.ONE))
          .createTransaction(senderKeys);

  protected MainnetTransactionValidator createTransactionValidator(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final int maxInitcodeSize) {
    return new MainnetTransactionValidator(
        gasCalculator,
        gasLimitCalculator,
        feeMarket,
        checkSignatureMalleability,
        chainId,
        acceptedTransactionTypes,
        maxInitcodeSize);
  }

  protected TransactionValidator createTransactionValidator(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId) {
    return createTransactionValidator(
        gasCalculator,
        gasLimitCalculator,
        FeeMarket.legacy(),
        checkSignatureMalleability,
        chainId,
        Set.of(TransactionType.FRONTIER),
        Integer.MAX_VALUE);
  }

  @Test
  void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
    final Transaction transaction =
        new TransactionTestFixture()
            .gasLimit(10)
            .chainId(Optional.empty())
            .createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(50L);

    assertThat(
            validator.validate(
                transaction, Optional.empty(), Optional.empty(), transactionValidationParams))
        .isEqualTo(
            ValidationResult.invalid(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());
    assertThat(
            validator.validate(
                basicTransaction, Optional.empty(), Optional.empty(), transactionValidationParams))
        .isEqualTo(
            ValidationResult.invalid(
                TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  void shouldRejectTransactionWhenTransactionHasIncorrectChainId() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            false,
            Optional.of(BigInteger.valueOf(2)));
    assertThat(
            validator.validate(
                basicTransaction, Optional.empty(), Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.WRONG_CHAIN_ID));
  }

  @Test
  void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));
    assertThat(validator.validateForSender(basicTransaction, null, processingBlockParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, processingBlockParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
  }

  @Test
  void shouldRejectTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsNotAllowed() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, processingBlockParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH));
  }

  @Test
  void shouldAcceptTransactionWhenTransactionNonceAboveAccountNonceAndFutureNonceIsAllowed() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, transactionPoolParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, processingBlockParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH));
  }

  @Test
  void transactionWithNullSenderCanBeValidIfGasPriceAndValueIsZero() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.of(BigInteger.ONE));

    final TransactionTestFixture builder = new TransactionTestFixture();
    final KeyPair senderKeyPair = SIGNATURE_ALGORITHM.get().generateKeyPair();
    final Address arbitrarySender = Address.fromHexString("1");
    builder.gasPrice(Wei.ZERO).nonce(0).sender(arbitrarySender).value(Wei.ZERO);

    assertThat(
            validator.validateForSender(
                builder.createTransaction(senderKeyPair), null, processingBlockParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldRejectTransactionIfAccountIsNotEOA() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator, GasLimitCalculator.constant(), false, Optional.empty());

    Account invalidEOA =
        when(account(basicTransaction.getUpfrontCost(0L), basicTransaction.getNonce())
                .getCodeHash())
            .thenReturn(Hash.fromHexStringLenient("0xdeadbeef"))
            .getMock();

    assertThat(validator.validateForSender(basicTransaction, invalidEOA, processingBlockParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED));
  }

  @Test
  void shouldRejectTransactionWithMaxFeeTimesGasLimitGreaterThanBalance() {
    final TransactionValidator validator =
        createTransactionValidator(
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
                transactionPoolParams))
        .isEqualTo(ValidationResult.invalid(UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  void shouldRejectTransactionWithMaxPriorityFeeGreaterThanMaxFee() {
    final TransactionValidator validator =
        createTransactionValidator(
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
        validator.validate(
            transaction, Optional.of(Wei.ONE), Optional.empty(), transactionValidationParams);
    assertThat(validationResult)
        .isEqualTo(ValidationResult.invalid(MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS));
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("max priority fee per gas cannot be greater than max fee per gas");
  }

  @Test
  void shouldRejectTransactionWithMaxBlobPriorityFeeSmallerThanBlobBaseFee() {
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.cancun(0L, Optional.empty()),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(
                new TransactionType[] {
                  TransactionType.FRONTIER,
                  TransactionType.ACCESS_LIST,
                  TransactionType.EIP1559,
                  TransactionType.BLOB
                }),
            Integer.MAX_VALUE);

    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(1);

    final Transaction transaction =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0xDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF")))
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.ONE))
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerBlobGas(Optional.of(Wei.of(7)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .blobsWithCommitments(Optional.of(bwc))
            .versionedHashes(Optional.of(bwc.getVersionedHashes()))
            .createTransaction(senderKeys);

    final ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(
            transaction,
            Optional.of(Wei.ONE),
            Optional.of(Wei.of(10)),
            transactionValidationParams);
    assertThat(validationResult)
        .isEqualTo(ValidationResult.invalid(BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE));
    assertThat(validationResult.getErrorMessage())
        .matches(
            "tx max fee per blob gas less than block blob gas fee: address 0x[0-9a-f]+ blobGasFeeCap: 7 wei, blobBaseFee: 10 wei");
  }

  @Test
  void shouldAcceptOnlyTransactionsInAcceptedTransactionTypes() {
    final TransactionValidator frontierValidator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.legacy(),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER),
            Integer.MAX_VALUE);

    final TransactionValidator eip1559Validator =
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
            frontierValidator.validate(
                transaction, Optional.empty(), Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(INVALID_TRANSACTION_FORMAT));

    when(gasCalculator.transactionIntrinsicGasCost(any(), anyBoolean())).thenReturn(0L);

    assertThat(
            eip1559Validator.validate(
                transaction, Optional.of(Wei.ONE), Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldRejectTransactionIfEIP1559TransactionGasPriceLessBaseFee() {
    final TransactionValidator validator =
        createTransactionValidator(
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
    assertThat(
            validator.validate(transaction, basefee, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(GAS_PRICE_BELOW_CURRENT_BASE_FEE));
  }

  @Test
  void shouldAcceptZeroGasPriceTransactionIfBaseFeeIsZero() {
    final Optional<Wei> zeroBaseFee = Optional.of(Wei.ZERO);
    final TransactionValidator validator =
        createTransactionValidator(
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

    assertThat(
            validator.validate(
                transaction, zeroBaseFee, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldAcceptValidEIP1559() {
    final TransactionValidator validator =
        createTransactionValidator(
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

    assertThat(
            validator.validate(transaction, basefee, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldValidate1559TransactionWithPriceLowerThanBaseFeeForTransactionPool() {
    final TransactionValidator validator =
        createTransactionValidator(
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
                transaction,
                Optional.of(Wei.ONE),
                Optional.empty(),
                TransactionValidationParams.transactionPool()))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  void shouldRejectTooLargeInitcode() {
    final TransactionValidator validator =
        createTransactionValidator(
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
        validator.validate(
            bigPayload, Optional.empty(), Optional.empty(), transactionValidationParams);

    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.INITCODE_TOO_LARGE);
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Initcode size of 49153 exceeds maximum size of 49152");
  }

  @Test
  void shouldRejectContractCreateWithBlob() {
    /*
    https://github.com/ethereum/EIPs/blob/master/EIPS/eip-4844.md#blob-transaction
    "The field to deviates slightly from the semantics with the exception that it
    MUST NOT be nil and therefore must always represent a 20-byte address.
    This means that blob transactions cannot have the form of a create transaction."
     */
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.cancun(0L, Optional.empty()),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559, TransactionType.BLOB),
            0xc000);

    var blobTx =
        new TransactionTestFixture()
            .to(Optional.empty())
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.ONE))
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerBlobGas(Optional.of(Wei.of(128)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .blobsWithCommitments(
                Optional.of(
                    new BlobsWithCommitments(
                        List.of(new KZGCommitment(Bytes48.ZERO)),
                        List.of(new Blob(Bytes.EMPTY)),
                        List.of(new KZGProof(Bytes48.ZERO)),
                        List.of(VersionedHash.DEFAULT_VERSIONED_HASH))))
            .versionedHashes(Optional.of(List.of(VersionedHash.DEFAULT_VERSIONED_HASH)))
            .createTransaction(senderKeys);
    var validationResult =
        validator.validate(
            blobTx, Optional.empty(), Optional.of(Wei.of(15)), transactionValidationParams);
    if (!validationResult.isValid()) {
      System.out.println(
          validationResult.getInvalidReason() + " " + validationResult.getErrorMessage());
    }

    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.INVALID_TRANSACTION_FORMAT);
  }

  @Test
  void shouldAcceptTransactionWithAtLeastOneBlob() {
    when(gasCalculator.blobGasCost(anyInt())).thenReturn(2L);
    final TransactionValidator validator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.cancun(0L, Optional.empty()),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(TransactionType.FRONTIER, TransactionType.EIP1559, TransactionType.BLOB),
            0xc000);

    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(1);
    var blobTx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0xDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF")))
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.ONE))
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerBlobGas(Optional.of(Wei.of(128)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .blobsWithCommitments(Optional.of(bwc))
            .versionedHashes(Optional.of(bwc.getVersionedHashes()))
            .createTransaction(senderKeys);
    var validationResult =
        validator.validate(
            blobTx, Optional.empty(), Optional.of(Wei.of(15)), transactionValidationParams);
    if (!validationResult.isValid()) {
      System.out.println(
          validationResult.getInvalidReason() + " " + validationResult.getErrorMessage());
    }

    assertThat(validationResult.isValid()).isTrue();
  }

  private static Stream<Arguments> provideValidateInitcodeTransactions() {
    Transaction.Builder base =
        Transaction.builder()
            .chainId(BigInteger.TEN)
            .nonce(22)
            .maxPriorityFeePerGas(Wei.of(2_000_000_000L))
            .maxFeePerGas(Wei.of(200_000_000_000L))
            .gasLimit(8_000_000)
            .to(Address.fromHexString("0xabcdef0987654321"))
            .value(Wei.ZERO)
            .payload(Bytes.fromHexString("0x87654321"))
            .signature(
                new SECP256K1()
                    .createSignature(
                        Bytes.fromHexString(
                                "0x2222222222222222222222222222222222222222222222222222222222222222")
                            .toUnsignedBigInteger(),
                        Bytes.fromHexString(
                                "0x2222222222222222222222222222222222222222222222222222222222222222")
                            .toUnsignedBigInteger(),
                        (byte) 0));

    Bytes[] maxCodes = new Bytes[257];
    Arrays.fill(maxCodes, Bytes.fromHexString("0xabcdef"));
    Bytes[] bigMaxCodes = new Bytes[257];
    Arrays.fill(bigMaxCodes, Bytes.repeat((byte) 0xef, 0xc000));
    Bytes tooBig = Bytes.repeat((byte) 0xef, 0xc001);

    return Stream.of(
        Arguments.of(
            "single",
            base.initcodes(List.of(Bytes.fromHexString("123456"))).build(),
            ValidationResult.<TransactionInvalidReason>valid()),
        Arguments.of(
            "double",
            base.initcodes(List.of(Bytes.fromHexString("123456"), Bytes.fromHexString("123456")))
                .build(),
            ValidationResult.<TransactionInvalidReason>valid()),
        Arguments.of(
            "max",
            base.initcodes(List.of(Arrays.copyOfRange(maxCodes, 0, 256))).build(),
            ValidationResult.<TransactionInvalidReason>valid()),
        Arguments.of(
            "bigmax",
            base.initcodes(List.of(Arrays.copyOfRange(bigMaxCodes, 0, 256))).build(),
            ValidationResult.<TransactionInvalidReason>valid()),
        Arguments.of(
            "too many",
            base.initcodes(List.of(maxCodes)).build(),
            ValidationResult.invalid(INVALID_INITCODE_LIST)),
        Arguments.of(
            "too big first",
            base.initcodes(List.of(tooBig)).build(),
            ValidationResult.invalid(INVALID_INITCODE_LIST)),
        Arguments.of(
            "too big second",
            base.initcodes(List.of(maxCodes[0], tooBig)).build(),
            ValidationResult.invalid(INVALID_INITCODE_LIST)),
        Arguments.of(
            "missing to",
            base.initcodes(List.of(maxCodes[0])).to(null).build(),
            ValidationResult.invalid(INVALID_INITCODE_TX_TARGET)));
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("provideValidateInitcodeTransactions")
  void validateInitcodeTransactions(
      final String ignoredName,
      final Transaction transaction,
      final ValidationResult<TransactionInvalidReason> validationResult) {
    final MainnetTransactionValidator validator =
        createTransactionValidator(
            gasCalculator,
            GasLimitCalculator.constant(),
            FeeMarket.cancun(0L, Optional.empty()),
            false,
            Optional.of(BigInteger.ONE),
            Set.of(
                TransactionType.FRONTIER,
                TransactionType.EIP1559,
                TransactionType.BLOB,
                TransactionType.INITCODE),
            0xc000);

    ValidationResult<TransactionInvalidReason> result =
        validator.validateInitcodeTransaction(transaction);

    assertThat(result.isValid()).isEqualTo(validationResult.isValid());
    if (!result.isValid()) {
      assertThat(result.getInvalidReason()).isEqualTo(validationResult.getInvalidReason());
    }
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
}
