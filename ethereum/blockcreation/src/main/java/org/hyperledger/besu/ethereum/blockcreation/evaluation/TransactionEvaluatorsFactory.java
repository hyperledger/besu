package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;

import java.util.ArrayList;
import java.util.List;

class TransactionEvaluatorsFactory {
  public static TransactionSelectionSelectorsList createEvaluators(
      final BlockSelectionContext context) {
    TransactionSelectionSelectorsList selectors = new TransactionSelectionSelectorsList();
    selectors.addTransactionSelector(new BlockSizeSelector(context));
    selectors.addTransactionSelector(new TransactionPriceSelector(context));
    return selectors;
  }

  public static class TransactionSelectionSelectorsList {

    private final List<TransactionSelector> selectors = new ArrayList<>();

    public void addTransactionSelector(final TransactionSelector selector) {
      selectors.add(selector);
    }

    public List<TransactionSelector> getTransactionSelectors() {
      return selectors;
    }
  }
}
