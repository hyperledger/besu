package org.hyperledger.besu.plugin.data;

public enum TransactionSelectionResult {
  DELETE_TRANSACTION_AND_CONTINUE,
  CONTINUE,
  COMPLETE_OPERATION
}
