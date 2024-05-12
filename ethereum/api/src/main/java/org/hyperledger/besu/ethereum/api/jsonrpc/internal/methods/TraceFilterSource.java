/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.ArrayList;
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
    if (!blockIterator.hasNext()) {
      return null;
    }

    currentBlock = blockIterator.next();
    List<Transaction> transactions = currentBlock.getBody().getTransactions();
    List<TransactionTrace> transactionTraces = new ArrayList<>(transactions.size() + 1);

    for (Transaction transaction : transactions) {
      transactionTraces.add(new TransactionTrace(transaction, Optional.of(currentBlock)));
    }

    transactionTraces.add(new TransactionTrace(Optional.of(currentBlock)));
    return transactionTraces.iterator();
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
