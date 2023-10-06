package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;

/** A TransactionSelector that unconditionally selects all transactions. */
public class AllAcceptingTransactionSelector implements TransactionSelector {
  public static final AllAcceptingTransactionSelector INSTANCE =
      new AllAcceptingTransactionSelector();

  private AllAcceptingTransactionSelector() {}

  /**
   * Always selects the transaction in the pre-processing stage.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @return Always SELECTED.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final PendingTransaction pendingTransaction) {
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Always selects the transaction in the post-processing stage.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @param processingResult The result of the transaction processing.
   * @return Always SELECTED.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final PendingTransaction pendingTransaction,
      final TransactionProcessingResult processingResult) {
    return TransactionSelectionResult.SELECTED;
  }
}
