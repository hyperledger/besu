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
package org.hyperledger.besu.ethereum.chain;

public class ChainPrunerConfiguration {
  public static final ChainPrunerConfiguration DEFAULT =
      new ChainPrunerConfiguration(false, 7200, 7200, 256);
  private final boolean enabled;
  private final long blocksRetained;
  private final long blocksFrequency;
  private final long blocksRetainedLimit;

  public ChainPrunerConfiguration(
      final boolean enabled,
      final long blocksRetained,
      final long blocksRetainedLimit,
      final long blocksFrequency) {
    this.enabled = enabled;
    this.blocksRetained = blocksRetained;
    this.blocksRetainedLimit = blocksRetainedLimit;
    this.blocksFrequency = blocksFrequency;
  }

  public long getChainPruningBlocksRetained() {
    return blocksRetained;
  }

  public long getBlocksRetainedLimit() {
    return blocksRetainedLimit;
  }

  public boolean getChainPruningEnabled() {
    return enabled;
  }

  public long getChainPruningBlocksFrequency() {
    return blocksFrequency;
  }
}
