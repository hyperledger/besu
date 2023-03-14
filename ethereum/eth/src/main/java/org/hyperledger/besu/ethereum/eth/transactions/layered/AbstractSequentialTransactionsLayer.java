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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.function.BiFunction;

public abstract class AbstractSequentialTransactionsLayer extends AbstractTransactionsLayer {

  public AbstractSequentialTransactionsLayer(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final TransactionPoolMetrics metrics) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
  }

  @Override
  protected boolean gapsAllowed() {
    return false;
  }

  @Override
  protected void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce,
      final PendingTransaction highestNonceRemovedTx) {
    // no -op
  }

  @Override
  protected void internalEvict(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    internalRemove(senderTxs, evictedTx);
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    final OptionalLong nextLayerRes = nextLayer.getNextNonceFor(sender);
    if (nextLayerRes.isEmpty()) {
      final var senderTxs = txsBySender.get(sender);
      if (senderTxs != null) {
        return OptionalLong.of(senderTxs.lastKey() + 1);
      }
    }
    return nextLayerRes;
  }

  @Override
  protected void internalNotifyAdded(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction) {
    // no-op
  }
}
