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
package org.hyperledger.besu.ethereum.trie.diffbased.common.worldview;

/** WorldStateConfig encapsulates the shared configuration parameters for the world state. */
public class WorldStateConfig {

  /**
   * Indicates whether the trie is disabled for the world state. When the trie is disabled, the
   * world state will only work with the flat database and not the trie. In this mode, it's
   * impossible to verify the state root.
   */
  private boolean isTrieDisabled;

  /** Indicates whether the mode is stateful. Default is true. */
  private boolean isStateful;

  private WorldStateConfig(final Builder builder) {
    this.isTrieDisabled = builder.isTrieDisabled;
    this.isStateful = builder.isStateful;
  }

  public boolean isTrieDisabled() {
    return isTrieDisabled;
  }

  public boolean isStateful() {
    return isStateful;
  }

  public void setTrieDisabled(final boolean trieDisabled) {
    isTrieDisabled = trieDisabled;
  }

  public void setStateful(final boolean stateful) {
    isStateful = stateful;
  }

  /**
   * Merges this WorldStateConfig with another WorldStateConfig and returns a new instance.
   *
   * @param other the other WorldStateConfig to merge with
   * @return a new WorldStateConfig instance with merged values
   */
  public WorldStateConfig apply(final WorldStateConfig other) {
    return new Builder(this).trieDisabled(other.isTrieDisabled).stateful(other.isStateful).build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(final WorldStateConfig worldStateConfig) {
    return new Builder(worldStateConfig);
  }

  public static WorldStateConfig createStatefulConfigWithTrie() {
    return newBuilder().stateful(true).trieDisabled(false).build();
  }

  public static class Builder {
    private boolean isStateful = true;
    private boolean isTrieDisabled = false;

    public Builder() {}

    public Builder(final WorldStateConfig spec) {
      this.isTrieDisabled = spec.isTrieDisabled();
      this.isStateful = spec.isStateful();
    }

    public Builder trieDisabled(final boolean trieDisabled) {
      this.isTrieDisabled = trieDisabled;
      return this;
    }

    public Builder stateful(final boolean stateful) {
      this.isStateful = stateful;
      return this;
    }

    public WorldStateConfig build() {
      return new WorldStateConfig(this);
    }
  }
}
