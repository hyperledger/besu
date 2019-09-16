/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.graphql;

import org.hyperledger.besu.ethereum.api.graphql.internal.BlockchainQuery;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class GraphQLDataFetcherContext {

  private final BlockchainQuery blockchain;
  private final MiningCoordinator miningCoordinator;
  private final Synchronizer synchronizer;
  private final ProtocolSchedule<?> protocolSchedule;
  private final TransactionPool transactionPool;

  public GraphQLDataFetcherContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Synchronizer synchronizer) {
    this.blockchain = new BlockchainQuery(blockchain, worldStateArchive);
    this.protocolSchedule = protocolSchedule;
    this.miningCoordinator = miningCoordinator;
    this.synchronizer = synchronizer;
    this.transactionPool = transactionPool;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  public BlockchainQuery getBlockchainQuery() {
    return blockchain;
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
