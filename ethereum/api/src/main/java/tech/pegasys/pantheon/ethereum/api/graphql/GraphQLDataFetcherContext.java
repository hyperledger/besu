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
package tech.pegasys.pantheon.ethereum.api.graphql;

import tech.pegasys.pantheon.ethereum.api.graphql.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

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
