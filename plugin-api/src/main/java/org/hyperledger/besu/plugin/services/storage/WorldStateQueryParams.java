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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;

public interface WorldStateQueryParams {
  /**
   * Gets the block header.
   *
   * @return the block header
   */
  BlockHeader getBlockHeader();

  /**
   * Checks if the world state should update the node head.
   *
   * @return true if the world state should update the node head, false otherwise
   */
  boolean shouldWorldStateUpdateHead();

  /**
   * Gets the block hash.
   *
   * @return the block hash
   */
  Hash getBlockHash();

  /**
   * Gets the state root.
   *
   * @return the state root
   */
  Optional<Hash> getStateRoot();
}
