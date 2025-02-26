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
package org.hyperledger.besu.ethereum.trie.diffbased.common.preload;

import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;

/**
 * PreloaderManager encapsulates the consumers used to preload account and storage data.
 *
 * <p>This abstract class defines the methods that need to be implemented in order to obtain
 * consumers for preloading accounts and storage slot keys from the world state. The preloading
 * mechanism is used to improve performance by loading required data before it is needed.
 *
 * @param <ACCOUNT> the type representing a diff-based account.
 */
public abstract class PreloaderManager<ACCOUNT> {

  /**
   * Returns a consumer responsible for preloading diff-based account values from the given world
   * state.
   *
   * <p>This method should be implemented to supply a Consumer that handles the preloading of
   * account data, ensuring that any necessary state is available when processing state transitions.
   *
   * @param worldState the diff-based world state from which to preload account data.
   * @return a Consumer that preloads DiffBasedValue&lt;ACCOUNT&gt; instances.
   */
  public abstract Consumer<DiffBasedValue<ACCOUNT>> getAccountPreloader(
      final DiffBasedWorldState worldState);

  /**
   * Returns a consumer responsible for preloading storage slot keys from the given world state.
   *
   * <p>This method should be implemented to supply a Consumer that handles the preloading of
   * storage data. Preloading storage keys can help in optimizing state lookups and transitions.
   *
   * @param worldState the diff-based world state from which to preload storage slot keys.
   * @return a Consumer that preloads StorageSlotKey instances.
   */
  public abstract Consumer<StorageSlotKey> getStoragePreloader(
      final DiffBasedWorldState worldState);
}
