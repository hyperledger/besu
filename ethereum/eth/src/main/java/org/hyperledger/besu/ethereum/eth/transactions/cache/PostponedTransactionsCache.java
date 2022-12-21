/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions.cache;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PostponedTransactionsCache {

  CompletableFuture<List<PendingTransaction>> promoteForSender(
      Address sender, long lastReadyNonce, long maxSize);

  CompletableFuture<List<PendingTransaction>> promote(int maxPromotable, long maxSize);

  void add(PendingTransaction pendingTransaction);

  void addAll(List<PendingTransaction> evictedTransactions);

  void remove(Transaction transaction);

  void removeForSenderBelowNonce(Address sender, long maxConfirmedNonce);
}
