package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPriceSelector extends AbstractTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPriceSelector.class);
  private final Wei minTransactionGasPrice;
  private final ProcessableBlockHeader processableBlockHeader;
  private final FeeMarket feeMarket;
  private final Wei blobGasPrice;
  private final TransactionPool transactionPool;

  public TransactionPriceSelector(final BlockSelectionContext context) {
    minTransactionGasPrice = context.minTransactionGasPrice();
    processableBlockHeader = context.processableBlockHeader();
    feeMarket = context.feeMarket();
    blobGasPrice = context.blobGasPrice();
    transactionPool = context.transactionPool();
  }

  @Override
  public TransactionSelectionResult evaluate(
      final Transaction transaction) {
    if (transactionCurrentPriceBelowMin(transaction)) {
      return TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN;
    }
    if (transactionDataPriceBelowMin(transaction)) {
      return TransactionSelectionResult.DATA_PRICE_BELOW_CURRENT_MIN;
    }
    return TransactionSelectionResult.SELECTED;
  }

  private boolean transactionCurrentPriceBelowMin(final Transaction transaction) {
    // Here we only care about EIP1159 since for Frontier and local transactions the checks
    // that we do when accepting them in the pool are enough
    if (transaction.getType().supports1559FeeMarket()
        && !transactionPool.isLocalSender(transaction.getSender())) {

      // For EIP1559 transactions, the price is dynamic and depends on network conditions, so we can
      // only calculate at this time the current minimum price the transaction is willing to pay
      // and if it is above the minimum accepted by the node.
      // If below we do not delete the transaction, since when we added the transaction to the pool,
      // we assured sure that the maxFeePerGas is >= of the minimum price accepted by the node
      // and so the price of the transaction could satisfy this rule in the future
      final Wei currentMinTransactionGasPriceInBlock =
          feeMarket
              .getTransactionPriceCalculator()
              .price(transaction, processableBlockHeader.getBaseFee());
      if (minTransactionGasPrice.compareTo(currentMinTransactionGasPriceInBlock) > 0) {
        LOG.trace(
            "Current gas fee of {} is lower than configured minimum {}, skipping",
            transaction,
            minTransactionGasPrice);
        return true;
      }
    }
    return false;
  }

  private boolean transactionDataPriceBelowMin(final Transaction transaction) {
    if (transaction.getType().supportsBlob()) {
      if (transaction.getMaxFeePerBlobGas().orElseThrow().lessThan(blobGasPrice)) {
        return true;
      }
    }
    return false;
  }
}
