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
package org.hyperledger.besu.ethereum.core.encoding;

/**
 * Enum representing the context in which a transaction is being encoded. This context is used to
 * determine the appropriate encoding strategy for a transaction.
 *
 * <p>The context can be one of the following:
 *
 * <ul>
 *   <li>{@link #BLOCK_BODY}: The transaction is part of a block body. This context is used when
 *       encoding transactions for inclusion in a block.
 *   <li>{@link #POOLED_TRANSACTION}: The transaction is part of a transaction pool. This context is
 *       used when encoding transactions that are currently in the transaction pool, waiting to be
 *       included in a block. It is also used when encoding transactions for RPC calls related to
 *       the transaction pool.
 * </ul>
 */
public enum EncodingContext {
  /** Represents the context where the transaction is part of a block body. */
  BLOCK_BODY,

  /**
   * Represents the context where the transaction is part of a transaction pool. This context is
   * also used when encoding transactions for RPC calls related to the transaction pool.
   */
  POOLED_TRANSACTION,
}
