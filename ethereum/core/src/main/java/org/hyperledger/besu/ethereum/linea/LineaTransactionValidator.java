package org.hyperledger.besu.ethereum.linea;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

public class LineaTransactionValidator extends MainnetTransactionValidator {
  private final int maxCalldataSize;

  public LineaTransactionValidator(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final boolean goQuorumCompatibilityMode,
      final int maxCalldataSize) {
    super(
        gasCalculator,
        gasLimitCalculator,
        feeMarket,
        checkSignatureMalleability,
        chainId,
        acceptedTransactionTypes,
        goQuorumCompatibilityMode,
        Integer.MAX_VALUE);
    this.maxCalldataSize = maxCalldataSize >= 0 ? maxCalldataSize : Integer.MAX_VALUE;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final TransactionValidationParams transactionValidationParams) {

    if (transaction.getPayload().size() > maxCalldataSize) {
      return ValidationResult.invalid(
          TransactionInvalidReason.CALLDATA_TOO_LARGE,
          String.format(
              "Calldata size of %d exceeds maximum size of %s",
              transaction.getPayload().size(), maxCalldataSize));
    }

    return super.validate(transaction, baseFee, transactionValidationParams);
  }
}
