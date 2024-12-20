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
package org.hyperledger.besu.ethereum.api.graphql;

/**
 * Enum representing various context types for GraphQL.
 *
 * <p>These context types are used internally by GraphQL to manage different aspects of the system.
 */
public enum GraphQLContextType {
  /** Represents blockchain queries context. */
  BLOCKCHAIN_QUERIES,

  /** Represents protocol schedule context. */
  PROTOCOL_SCHEDULE,

  /** Represents transaction pool context. */
  TRANSACTION_POOL,

  /** Represents mining coordinator context. */
  MINING_COORDINATOR,

  /** Represents synchronizer context. */
  SYNCHRONIZER,

  /** Represents is alive handler context. */
  IS_ALIVE_HANDLER,

  /** Represents chain ID context. */
  CHAIN_ID,

  /** Represents the transaction simulator. */
  TRANSACTION_SIMULATOR
}
