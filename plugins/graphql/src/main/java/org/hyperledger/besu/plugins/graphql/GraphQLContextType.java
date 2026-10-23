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
package org.hyperledger.besu.plugins.graphql;

/**
 * Enum representing various context types for GraphQL.
 *
 * <p>These context types are the keys of the GraphQL context map that the data fetchers read the
 * plugin services from.
 */
public enum GraphQLContextType {
  /** The {@code BlockchainService}. */
  BLOCKCHAIN_SERVICE,

  /** The {@code BlockchainQueryService}. */
  BLOCKCHAIN_QUERY_SERVICE,

  /** The {@code WorldStateService}. */
  WORLD_STATE_SERVICE,

  /** The {@code TransactionSimulationService}. */
  TRANSACTION_SIMULATION_SERVICE,

  /** The {@code TransactionPoolService}. */
  TRANSACTION_POOL_SERVICE,

  /** The {@code RlpConverterService}. */
  RLP_CONVERTER_SERVICE,

  /** The {@code SynchronizationService}. */
  SYNCHRONIZATION_SERVICE,

  /**
   * Whether the node stores its world state in a Bonsai format, a {@code Boolean}. Account code and
   * storage are then read at the chain head unless a block is given, as Besu has always done for
   * Bonsai accounts.
   */
  BONSAI_WORLD_STATE,

  /** Represents is alive handler context, a {@code Supplier<Boolean>}. */
  IS_ALIVE_HANDLER
}
