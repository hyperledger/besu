/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.types;

/**
 * The mode in which the block hash is calculated for a QBFT block.
 *
 * <p>When a block is hashed, the hash may be calculated in different ways depending on the context
 * in which the hash is being used.
 */
public enum QbftHashMode {
  /**
   * Hash the block for the committed seal. This typically means the block hash excludes the commit
   * seal from the hashing.
   */
  COMMITTED_SEAL,

  /**
   * Hash the block for onchain block. This typically means the block hash exclude the commit seals
   * and round number from the hashing as each node may have a different value for these fields.
   */
  ONCHAIN
}
