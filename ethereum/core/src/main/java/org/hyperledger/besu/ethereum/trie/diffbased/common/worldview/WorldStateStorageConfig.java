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

public class WorldStateStorageConfig {

  private final boolean isFrozen;

  private WorldStateStorageConfig(final Builder builder) {
    this.isFrozen = builder.isFrozen;
  }

  public boolean isFrozen() {
    return isFrozen;
  }

  public static WorldStateStorageConfig.Builder newBuilder() {
    return new WorldStateStorageConfig.Builder();
  }

  public static WorldStateStorageConfig.Builder newBuilder(
      final WorldStateStorageConfig worldStateStorageConfig) {
    return new WorldStateStorageConfig.Builder(worldStateStorageConfig);
  }

  public static class Builder {
    private boolean isFrozen = false;

    public Builder() {}

    public Builder(final WorldStateStorageConfig spec) {
      this.isFrozen = spec.isFrozen();
    }

    public Builder setFrozen(final boolean isFrozen) {
      this.isFrozen = isFrozen;
      return this;
    }

    public WorldStateStorageConfig build() {
      return new WorldStateStorageConfig(this);
    }
  }
}
