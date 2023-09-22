package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;

import java.util.List;

public class AbstractTransactionSelector implements TransactionSelector {

  public TransactionSelectionResult evaluate(Transaction transaction) {
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluate(org.hyperledger.besu.datatypes.Transaction transaction) {
    return null;
  }
}
