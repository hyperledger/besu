package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class TraceFilterSource implements Iterator<TransactionTrace> {

  private final ArrayNodeWrapper resultArrayNode;
  private final Iterator<Block> blockIterator;
  private Iterator<TransactionTrace> transactionTraceIterator;
  private Block currentBlock;

  public TraceFilterSource(final List<Block> blockList, final ArrayNodeWrapper resultArrayNode) {
    this.resultArrayNode = resultArrayNode;
    this.blockIterator = blockList.iterator();
    this.transactionTraceIterator = getNextTransactionIterator();
  }

  private Iterator<TransactionTrace> getNextTransactionIterator() {
    if (blockIterator.hasNext()) {
      currentBlock = blockIterator.next();
      if (currentBlock.getBody().getTransactions().size() == 0) {
        TransactionTrace rewardTrace = new TransactionTrace(Optional.of(currentBlock));
        return Collections.singleton(rewardTrace).iterator();
      } else {
        List<TransactionTrace> transactionTraces = new ArrayList<>();
        List<Transaction> transactions = currentBlock.getBody().getTransactions();
        int size = transactions.size();
        int index = 0;
        while (index <= size) {
          if (index == size) {
            transactionTraces.add(new TransactionTrace(Optional.of(currentBlock)));
          } else {
            transactionTraces.add(
                new TransactionTrace(transactions.get(index), Optional.of(currentBlock)));
          }
          index++;
        }
        return transactionTraces.iterator();
      }
    }
    return null;
  }

  @Override
  public boolean hasNext() {
    if (resultArrayNode.isFull()) return false;
    if (transactionTraceIterator == null) {
      return false;
    }
    if (transactionTraceIterator.hasNext()) {
      return true;
    }
    transactionTraceIterator = getNextTransactionIterator();
    return hasNext();
  }

  @Override
  public TransactionTrace next() {
    if (transactionTraceIterator == null) {
      return null;
    }
    if (transactionTraceIterator.hasNext()) {
      return transactionTraceIterator.next();
    }
    transactionTraceIterator = getNextTransactionIterator();
    return next();
  }
}
