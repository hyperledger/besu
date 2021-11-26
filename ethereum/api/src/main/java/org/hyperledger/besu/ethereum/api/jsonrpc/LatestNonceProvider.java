/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.util.NonceProvider;

import java.util.OptionalLong;

public class LatestNonceProvider implements NonceProvider {

  private final BlockchainQueries blockchainQueries;
  private final AbstractPendingTransactionsSorter pendingTransactions;

  public LatestNonceProvider(
      final BlockchainQueries blockchainQueries,
      final AbstractPendingTransactionsSorter pendingTransactions) {
    this.blockchainQueries = blockchainQueries;
    this.pendingTransactions = pendingTransactions;
  }

  @Override
  public long getNonce(final Address address) {
    final OptionalLong pendingNonce = pendingTransactions.getNextNonceForSender(address);
    return pendingNonce.orElseGet(
        () -> blockchainQueries.getTransactionCount(address, blockchainQueries.headBlockNumber()));
  }
}
