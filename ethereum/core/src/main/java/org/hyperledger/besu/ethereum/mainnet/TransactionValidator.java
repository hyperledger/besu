package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

public interface TransactionValidator {
  ValidationResult<TransactionInvalidReason> validate(
      Transaction transaction,
      Optional<Wei> baseFee,
      TransactionValidationParams transactionValidationParams);

  ValidationResult<TransactionInvalidReason> validateForSender(
      Transaction transaction, Account sender, TransactionValidationParams validationParams);

  ValidationResult<TransactionInvalidReason> validateTransactionSignature(Transaction transaction);

  void setTransactionFilter(TransactionFilter transactionFilter);
}
