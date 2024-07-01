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

import org.hyperledger.besu.ethereum.api.handlers.IsAliveHandler;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

/**
 * Interface representing the context for a GraphQL data fetcher.
 *
 * <p>This context provides access to various components of the system such as the transaction pool,
 * blockchain queries, mining coordinator, synchronizer, and protocol schedule.
 */
public interface GraphQLDataFetcherContext {

  /**
   * Retrieves the transaction pool.
   *
   * @return the transaction pool
   */
  TransactionPool getTransactionPool();

  /**
   * Retrieves the blockchain queries.
   *
   * @return the blockchain queries
   */
  BlockchainQueries getBlockchainQueries();

  /**
   * Retrieves the mining coordinator.
   *
   * @return the mining coordinator
   */
  MiningCoordinator getMiningCoordinator();

  /**
   * Retrieves the synchronizer.
   *
   * @return the synchronizer
   */
  Synchronizer getSynchronizer();

  /**
   * Retrieves the protocol schedule.
   *
   * @return the protocol schedule
   */
  ProtocolSchedule getProtocolSchedule();

  /**
   * Retrieves the is alive handler.
   *
   * <p>By default, this method returns a new IsAliveHandler instance with a status of true.
   *
   * @return the is alive handler
   */
  default IsAliveHandler getIsAliveHandler() {
    return new IsAliveHandler(true);
  }
}
