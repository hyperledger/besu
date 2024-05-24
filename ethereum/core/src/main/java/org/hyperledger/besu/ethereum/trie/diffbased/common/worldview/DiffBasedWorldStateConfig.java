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
package org.hyperledger.besu.ethereum.trie.diffbased.common.worldview;

public class DiffBasedWorldStateConfig {

  private boolean isFrozen;

  private boolean isTrieDisabled;

  public DiffBasedWorldStateConfig() {
    this(false, false);
  }

  public DiffBasedWorldStateConfig(final boolean isTrieDisabled) {
    this(false, isTrieDisabled);
  }

  public DiffBasedWorldStateConfig(final DiffBasedWorldStateConfig config) {
    this(config.isFrozen(), config.isTrieDisabled());
  }

  public DiffBasedWorldStateConfig(final boolean isFrozen, final boolean isTrieDisabled) {
    this.isFrozen = isFrozen;
    this.isTrieDisabled = isTrieDisabled;
  }

  /**
   * Checks if the world state is frozen. When the world state is frozen, it cannot mutate.
   *
   * @return true if the world state is frozen, false otherwise.
   */
  public boolean isFrozen() {
    return isFrozen;
  }

  /**
   * Sets the frozen status of the world state. When the world state is frozen, it cannot mutate.
   *
   * @param frozen the new frozen status to set.
   */
  public void setFrozen(final boolean frozen) {
    isFrozen = frozen;
  }

  /**
   * Checks if the trie is disabled for the world state. When the trie is disabled, the world state
   * will only work with the flat database and not the trie. In this mode, it's impossible to verify
   * the state root.
   *
   * @return true if the trie is disabled, false otherwise.
   */
  public boolean isTrieDisabled() {
    return isTrieDisabled;
  }

  /**
   * Sets the disabled status of the trie for the world state. When the trie is disabled, the world
   * state will only work with the flat database and not the trie. In this mode, it's impossible to
   * verify the state root.
   *
   * @param trieDisabled the new disabled status to set for the trie.
   */
  public void setTrieDisabled(final boolean trieDisabled) {
    isTrieDisabled = trieDisabled;
  }
}
