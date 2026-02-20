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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

/** Represents a block header in the context of the QBFT consensus mechanism. */
public interface QbftBlockHeader {

  /**
   * Returns the block number of the block.
   *
   * @return the block number.
   */
  long getNumber();

  /**
   * Returns the timestamp of the block.
   *
   * @return the timestamp.
   */
  long getTimestamp();

  /**
   * Returns the coinbase of the block.
   *
   * @return the coinbase.
   */
  Address getCoinbase();

  /**
   * Returns the hash of the block.
   *
   * @return the hash.
   */
  Hash getHash();
}
