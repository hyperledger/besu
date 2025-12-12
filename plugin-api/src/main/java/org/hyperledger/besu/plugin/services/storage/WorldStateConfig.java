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

/**
 * WorldStateConfig encapsulates the shared configuration parameters for managing the Ethereum world
 * state, such as trie enablement and stateful operation.
 */
public interface WorldStateConfig {

  /**
   * Returns {@code true} if the trie is disabled for the world state.
   *
   * @return true if the trie is disabled, false otherwise
   */
  boolean isTrieDisabled();

  /**
   * Returns {@code true} if the world state is stateful.
   *
   * @return true if the mode is stateful, false otherwise
   */
  boolean isStateful();

  /**
   * Sets whether the trie is disabled for the world state.
   *
   * @param trieDisabled true to disable the trie, false otherwise
   */
  void setTrieDisabled(final boolean trieDisabled);

  /**
   * Sets whether the world state is stateful.
   *
   * @param stateful true if stateful, false otherwise
   */
  void setStateful(final boolean stateful);
}
