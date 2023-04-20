package linea;

import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

public class LineaTransactionSelectorFactory implements TransactionSelectorFactory {

  public static final String NAME = "linea";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public TransactionSelector create() {
    return new LineaTransactionSelector();
  }
}
