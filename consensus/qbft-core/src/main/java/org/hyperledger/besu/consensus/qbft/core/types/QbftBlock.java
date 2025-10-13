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

import org.hyperledger.besu.datatypes.Hash;

/** Represents a block in the context of the QBFT consensus mechanism. */
public interface QbftBlock {

  /**
   * Returns the block header of the block.
   *
   * @return the block header.
   */
  QbftBlockHeader getHeader();

  /**
   * Whether the block is considered empty, generally this means that the block has no transactions.
   *
   * @return true if the block is empty, false otherwise.
   */
  boolean isEmpty();

  /**
   * Returns the hash of the header. A convenience method to avoid having to call
   * getHeader().getHash().
   *
   * @return the hash of the block.
   */
  default Hash getHash() {
    return getHeader().getHash();
  }
}
