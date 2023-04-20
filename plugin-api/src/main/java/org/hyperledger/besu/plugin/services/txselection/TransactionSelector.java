package org.hyperledger.besu.plugin.services.txselection;

import org.hyperledger.besu.plugin.data.Transaction;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

public interface TransactionSelector {

  TransactionSelectionResult selectTransaction(
      final Transaction transaction, final TransactionReceipt receipt);

  //    default Object getTracer() {
  //        return OperationTracer.NO_TRACING;
  //    }
}
