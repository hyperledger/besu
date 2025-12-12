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

/**
 * Encapsulates parameters required for querying the Ethereum world state.
 *
 * <p>This class defines fields such as the block header, block hash, state root, and a flag
 * indicating whether the world state should update the node head. It supports construction via a
 * builder pattern and provides several static convenience methods for common query configurations.
 *
 * <p>Instances of {@code WorldStateQueryParams} are immutable and used to provide context and
 * constraints for operations that query the world state in relation to a particular block or state
 * root.
 */
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
