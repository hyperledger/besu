package org.hyperledger.besu.ethereum.linea;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

public class LineaTransactionValidator implements TransactionValidator {
  private final TransactionValidator baseTxValidator;
  private final int maxCalldataSize;

  public LineaTransactionValidator(
      final TransactionValidator baseValidator, final int maxCalldataSize) {
    this.baseTxValidator = baseValidator;
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

    return baseTxValidator.validate(transaction, baseFee, transactionValidationParams);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {
    return baseTxValidator.validateForSender(transaction, sender, validationParams);
  }
}
