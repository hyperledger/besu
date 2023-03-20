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
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionSource implements Iterator<TransactionTrace> {

  private final List<Transaction> transactions;
  private final AtomicInteger currentIndex = new AtomicInteger(0);
  private final Block block;

  public TransactionSource(final Block block) {
    this.block = block;
    this.transactions = block.getBody().getTransactions();
  }

  @Override
  public boolean hasNext() {
    return currentIndex.get() < (transactions.size());
  }

  @Override
  public TransactionTrace next() {
    return new TransactionTrace(
        transactions.get(currentIndex.getAndIncrement()), Optional.of(block));
  }
}
