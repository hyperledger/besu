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

/** Internal GraphQL Context */
public enum GraphQLContextType {
  /** Blockchain queries graph ql context type. */
  BLOCKCHAIN_QUERIES,
  /** Protocol schedule graph ql context type. */
  PROTOCOL_SCHEDULE,
  /** Transaction pool graph ql context type. */
  TRANSACTION_POOL,
  /** Mining coordinator graph ql context type. */
  MINING_COORDINATOR,
  /** Synchronizer graph ql context type. */
  SYNCHRONIZER,
  /** Is alive handler graph ql context type. */
  IS_ALIVE_HANDLER,
  /** Chain id graph ql context type. */
  CHAIN_ID,
  /** Gas cap graph ql context type. */
  GAS_CAP
}
