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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

public class FilterManagerBuilder {

  private BlockchainQueries blockchainQueries;
  private TransactionPool transactionPool;
  private FilterIdGenerator filterIdGenerator = new FilterIdGenerator();
  private FilterRepository filterRepository = new FilterRepository();

  public FilterManagerBuilder filterIdGenerator(final FilterIdGenerator filterIdGenerator) {
    this.filterIdGenerator = filterIdGenerator;
    return this;
  }

  public FilterManagerBuilder filterRepository(final FilterRepository filterRepository) {
    this.filterRepository = filterRepository;
    return this;
  }

  public FilterManagerBuilder blockchainQueries(final BlockchainQueries blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
    return this;
  }

  public FilterManagerBuilder transactionPool(final TransactionPool transactionPool) {
    this.transactionPool = transactionPool;
    return this;
  }

  public FilterManager build() {
    if (blockchainQueries == null) {
      throw new IllegalStateException("BlockchainQueries is required to build FilterManager");
    }

    if (transactionPool == null) {
      throw new IllegalStateException("TransactionPool is required to build FilterManager");
    }

    return new FilterManager(
        blockchainQueries, transactionPool, filterIdGenerator, filterRepository);
  }
}
