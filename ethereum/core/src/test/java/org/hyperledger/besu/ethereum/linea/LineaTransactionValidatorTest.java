package org.hyperledger.besu.ethereum.linea;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidatorTest;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class LineaTransactionValidatorTest extends MainnetTransactionValidatorTest {
  private static final int TX_MAX_CALLDATA_SIZE = 1000;
  private final BigInteger chainId = BigInteger.valueOf(59140);
  private LineaTransactionValidator validator;

  @BeforeEach
  void setup() {
    validator = createValidator(TX_MAX_CALLDATA_SIZE);
  }

  @ParameterizedTest
  @MethodSource("transactionValidationParams")
  public void txWithNoCalldata(final TransactionValidationParams transactionValidationParams) {
    final Transaction transaction = createTransaction(0);

    assertThat(validator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @ParameterizedTest
  @MethodSource("transactionValidationParams")
  public void txWithCalldataBelowMax(
      final TransactionValidationParams transactionValidationParams) {
    final Transaction transaction = createTransaction(TX_MAX_CALLDATA_SIZE / 2);

    assertThat(validator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @ParameterizedTest
  @MethodSource("transactionValidationParams")
  public void txWithCalldataEqualMax(
      final TransactionValidationParams transactionValidationParams) {
    final Transaction transaction = createTransaction(TX_MAX_CALLDATA_SIZE);

    assertThat(validator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.valid());
  }

  @ParameterizedTest
  @MethodSource("transactionValidationParams")
  public void txWithCalldataAboveMax(
      final TransactionValidationParams transactionValidationParams) {
    final Transaction transaction = createTransaction(TX_MAX_CALLDATA_SIZE + 1);

    assertThat(validator.validate(transaction, Optional.empty(), transactionValidationParams))
        .isEqualTo(ValidationResult.invalid(TransactionInvalidReason.CALLDATA_TOO_LARGE));
  }

  private LineaTransactionValidator createValidator(final int txCalldataLimit) {
    return new LineaTransactionValidator(
        gasCalculator,
        GasLimitCalculator.constant(),
        FeeMarket.zeroBaseFee(0L),
        false,
        Optional.of(BigInteger.valueOf(59140)),
        EnumSet.allOf(TransactionType.class),
        txCalldataLimit);
  }

  private Transaction createTransaction(final int calldataSize) {
    final Transaction transaction =
        new TransactionTestFixture()
            .chainId(Optional.of(chainId))
            .payload(Bytes.random(calldataSize))
            .createTransaction(senderKeys);
    return transaction;
  }

  private static Stream<Arguments> transactionValidationParams() {
    return Stream.of(
        Arguments.of(TransactionValidationParams.processingBlockParams),
        Arguments.of(TransactionValidationParams.transactionPoolParams),
        Arguments.of(TransactionValidationParams.miningParams),
        Arguments.of(TransactionValidationParams.blockReplayParams),
        Arguments.of(TransactionValidationParams.transactionSimulatorParams));
  }
}
