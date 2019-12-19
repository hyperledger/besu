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
package org.hyperledger.besu.ethereum.api.graphql;

import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

public class GraphQLDataFetcherContext {

  private final BlockchainQueries blockchainQueries;
  private final MiningCoordinator miningCoordinator;
  private final Synchronizer synchronizer;
  private final ProtocolSchedule<?> protocolSchedule;
  private final TransactionPool transactionPool;

  public GraphQLDataFetcherContext(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Synchronizer synchronizer) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.miningCoordinator = miningCoordinator;
    this.synchronizer = synchronizer;
    this.transactionPool = transactionPool;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  public BlockchainQueries getBlockchainQueries() {
    return blockchainQueries;
  }

  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  public ProtocolSchedule<?> getProtocolSchedule() {
    return protocolSchedule;
  }
}
