package linea;

import org.hyperledger.besu.plugin.data.Transaction;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;

public class LineaTransactionSelector implements TransactionSelector {

  @Override
  public TransactionSelectionResult selectTransaction(
      final Transaction transaction, final TransactionReceipt receipt) {
    System.out.println(
        "I'm in the plugin! Transaction:\n" + transaction + "\nReceipt:\n" + receipt);
    return TransactionSelectionResult.CONTINUE;
  }
}
